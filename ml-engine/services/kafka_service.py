import os
import json
import logging
from datetime import datetime, timezone
from dotenv import load_dotenv
from kafka import KafkaProducer, KafkaConsumer
from kafka.errors import KafkaError, KafkaTimeoutError, NoBrokersAvailable
from services.mongo_service import append_payment_event

load_dotenv(".env")

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s: %(message)s")
logging.getLogger("kafka").setLevel(logging.DEBUG)
log = logging.getLogger("splitbill.kafka")

KAFKA_BOOTSTRAP_SERVERS = os.getenv("SPRING_KAFKA_BOOTSTRAP_SERVERS")
KAFKA_SSL_CAFILE = os.getenv("KAFKA_SSL_CAFILE") or os.getenv("KAFKA_SSL_TRUSTSTORE_LOCATION")
KAFKA_SSL_CERTFILE = os.getenv("KAFKA_SSL_CERTFILE") or os.getenv("KAFKA_SSL_CERT_LOCATION")
KAFKA_SSL_KEYFILE = os.getenv("KAFKA_SSL_KEYFILE") or os.getenv("KAFKA_SSL_KEY_LOCATION")
KAFKA_SSL_KEY_PASSWORD = os.getenv("KAFKA_SSL_KEY_PASSWORD")

SPLITBILL_CREATED_TOPIC = "splitbill.events.created"
SPLITBILL_REMINDED_TOPIC = "splitbill.events.reminded"
PAYMENT_INTENT_TOPIC = "payment.intent.created"
PAYMENT_STATUS_TOPIC = "payment.status.updated"

producer = None

def _is_ssl_enabled():
    """Check if SSL configuration is available and valid"""
    if not all([KAFKA_SSL_CAFILE, KAFKA_SSL_CERTFILE, KAFKA_SSL_KEYFILE]):
        return False
    return all(os.path.exists(p) for p in [KAFKA_SSL_CAFILE, KAFKA_SSL_CERTFILE, KAFKA_SSL_KEYFILE])

def _preflight():
    """Validate Kafka configuration"""
    if not KAFKA_BOOTSTRAP_SERVERS:
        raise RuntimeError("SPRING_KAFKA_BOOTSTRAP_SERVERS tidak terisi")

    use_ssl = _is_ssl_enabled()
    if use_ssl:
        log.info("Kafka SSL enabled - using mTLS authentication")
    else:
        log.info("Kafka SSL disabled - using PLAINTEXT protocol (local/dev mode)")

def _check_topic_exists(topic: str) -> bool:
    try:
        use_ssl = _is_ssl_enabled()
        config = {
            "bootstrap_servers": KAFKA_BOOTSTRAP_SERVERS,
            "request_timeout_ms": 10000,
            "session_timeout_ms": 10000,
            "api_version_auto_timeout_ms": 10000,
        }

        if use_ssl:
            config.update({
                "security_protocol": "SSL",
                "ssl_cafile": KAFKA_SSL_CAFILE,
                "ssl_certfile": KAFKA_SSL_CERTFILE,
                "ssl_keyfile": KAFKA_SSL_KEYFILE,
                "ssl_password": KAFKA_SSL_KEY_PASSWORD,
            })

        c = KafkaConsumer(**config)
        topics = c.topics()
        c.close()
        return topic in topics
    except Exception as e:
        log.error("Gagal cek topic %s: %s", topic, e, exc_info=True)
        return False

def get_kafka_producer():
    global producer
    if producer:
        return producer
    _preflight()

    use_ssl = _is_ssl_enabled()
    config = {
        "bootstrap_servers": KAFKA_BOOTSTRAP_SERVERS,
        "value_serializer": lambda v: json.dumps(v).encode("utf-8"),
        "key_serializer": lambda k: str(k).encode("utf-8"),
        "acks": "all",
        "retries": 3,
    }

    if use_ssl:
        config.update({
            "security_protocol": "SSL",
            "ssl_cafile": KAFKA_SSL_CAFILE,
            "ssl_certfile": KAFKA_SSL_CERTFILE,
            "ssl_keyfile": KAFKA_SSL_KEYFILE,
            "ssl_password": KAFKA_SSL_KEY_PASSWORD,
        })

    try:
        producer = KafkaProducer(**config)
        protocol = "SSL/mTLS" if use_ssl else "PLAINTEXT"
        log.info("KafkaProducer connected to %s (%s)", KAFKA_BOOTSTRAP_SERVERS, protocol)
        return producer
    except NoBrokersAvailable as e:
        log.error("No brokers available: %s", e, exc_info=True)
        raise
    except Exception as e:
        log.error("Init producer gagal: %s", e, exc_info=True)
        raise

def _send_sync(topic: str, key: str, value: dict):
    if not _check_topic_exists(topic):
        raise RuntimeError(f"Topic tidak ditemukan di broker: {topic}")
    prod = get_kafka_producer()
    try:
        fut = prod.send(topic, key=key, value=value)
        md = fut.get(timeout=10)
        log.info("Published to %s partition=%s offset=%s", md.topic, md.partition, md.offset)
    except KafkaTimeoutError as e:
        log.error("Timeout publish ke %s: %s", topic, e, exc_info=True)
        raise
    except KafkaError as e:
        log.error("KafkaError publish ke %s: %s", topic, e, exc_info=True)
        raise
    except Exception as e:
        log.error("Unexpected error publish ke %s: %s", topic, e, exc_info=True)
        raise

def publish_bill_created(bill_id, owner_user_id, owner_link, member_links):
    payload = {
        "billId": bill_id,
        "ownerUserId": owner_user_id,
        "ownerShortLink": owner_link,
        "memberLinks": member_links,
        "createdAt": datetime.now(timezone.utc).isoformat()
    }
    _send_sync(SPLITBILL_CREATED_TOPIC, key=bill_id, value=payload)

def publish_bill_reminded(bill_id, actor_user_id, channels, member_links, result_data):
    payload = {
        "billId": bill_id,
        "remindedByUserId": actor_user_id,
        "requestedChannels": channels,
        "memberLinks": member_links,
        "result": result_data,
        "remindedAt": datetime.now(timezone.utc).isoformat()
    }
    _send_sync(SPLITBILL_REMINDED_TOPIC, key=bill_id, value=payload)

def publish_payment_intent(bill_id, member_id, amount, source_wallet_id, destination_wallet_id):
    payload = {
        "billId": bill_id,
        "memberId": member_id,
        "amount": amount,
        "sourceWalletId": source_wallet_id,
        "destinationWalletId": destination_wallet_id,
        "intentAt": datetime.now(timezone.utc).isoformat()
    }
    _send_sync(PAYMENT_INTENT_TOPIC, key=f"{bill_id}:{member_id}", value=payload)

def start_consumer():
    use_ssl = _is_ssl_enabled()
    config = {
        "bootstrap_servers": KAFKA_BOOTSTRAP_SERVERS,
        "group_id": "split-bill-payment-updater",
        "value_deserializer": lambda v: json.loads(v.decode("utf-8")),
        "enable_auto_commit": True,
        "auto_offset_reset": "latest",
        "request_timeout_ms": 15000,
        "session_timeout_ms": 15000,
        "max_poll_interval_ms": 300000,
        "api_version_auto_timeout_ms": 10000,
    }

    if use_ssl:
        config.update({
            "security_protocol": "SSL",
            "ssl_cafile": KAFKA_SSL_CAFILE,
            "ssl_certfile": KAFKA_SSL_CERTFILE,
            "ssl_keyfile": KAFKA_SSL_KEYFILE,
            "ssl_password": KAFKA_SSL_KEY_PASSWORD,
        })

    try:
        consumer = KafkaConsumer(PAYMENT_STATUS_TOPIC, **config)
        protocol = "SSL/mTLS" if use_ssl else "PLAINTEXT"
        log.info("Consumer ready on topic %s (%s)", PAYMENT_STATUS_TOPIC, protocol)
    except Exception as e:
        log.error("Start consumer gagal: %s", e, exc_info=True)
        raise

    while True:
        try:
            msg_pack = consumer.poll(timeout_ms=5000)
            if not msg_pack:
                continue
            for _, msgs in msg_pack.items():
                for message in msgs:
                    try:
                        evt = message.value
                        append_payment_event(evt)
                        log.info("Processed payment event offset=%s", message.offset)
                    except Exception as ex:
                        log.error("Process message error: %s", ex, exc_info=True)
        except Exception as loop_err:
            log.error("Consumer loop error: %s", loop_err, exc_info=True)
