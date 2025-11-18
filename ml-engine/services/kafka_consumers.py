import os, json, signal, sys
from datetime import datetime
from bson import ObjectId
from confluent_kafka import Consumer, KafkaError, KafkaException

from services.mongo_service import bills_collection

TOPIC = "payment.status.updated.v1"
GROUP_ID = "split-bill-payment-updater"
KAFKA_BOOTSTRAP = os.getenv("KAFKA_BOOTSTRAP")
KAFKA_USERNAME = os.getenv("KAFKA_USERNAME")
KAFKA_PASSWORD = os.getenv("KAFKA_PASSWORD")
KAFKA_CA = os.getenv("KAFKA_CA")


def _processed_coll():
    if bills_collection is None:
        return None
    return bills_collection.database.get_collection("payment_events_processed")


def _recompute_bill_status(members):
    statuses = [m.get("status") for m in (members or [])]
    if statuses and all(s == "PAID" for s in statuses):
        return "PAID"
    if any(s == "PAID" for s in statuses):
        return "PARTIALLY_PAID"
    return "SENT"


def _apply_payment_success(doc: dict, member_id: str, amount: int | None = None):
    members = doc.get("members", [])
    updated_paid_total = int(doc.get("paid_total", 0))
    changed = False

    for m in members:
        if m.get("member_id") == member_id and m.get("status") != "PAID":
            due = int(m.get("amount_due", 0))
            prev_paid = int(m.get("paid", 0))
            if amount is not None and amount < due:
                due = amount
            m["status"] = "PAID"
            m["paid"] = due
            m["payment_method"] = "WALLET_TRANSFER"
            updated_paid_total += (due - prev_paid)
            changed = True

    if not changed:
        return False, doc.get("status"), updated_paid_total

    new_status = _recompute_bill_status(members)
    bills_collection.update_one(
        {"_id": doc["_id"]},
        {
            "$set": {
                "members": members,
                "paid_total": updated_paid_total,
                "status": new_status,
                "updated_at": datetime.utcnow(),
            }
        },
    )
    return True, new_status, updated_paid_total


def _save_payment_failure_note(bill_oid, member_id, reason: str | None):
    bills_collection.update_one(
        {"_id": bill_oid, "members.member_id": member_id},
        {
            "$set": {
                "members.$.last_failure": {
                    "reason": reason,
                    "at": datetime.utcnow(),
                }
            }
        },
    )


def _build_consumer():
    conf = {
        "bootstrap.servers": KAFKA_BOOTSTRAP,
        "group.id": GROUP_ID,
        "enable.auto.commit": False,
        "auto.offset.reset": "earliest",
    }
    if KAFKA_USERNAME and KAFKA_PASSWORD:
        conf.update(
            {
                "security.protocol": "SASL_SSL",
                "sasl.mechanisms": "SCRAM-SHA-256",
                "sasl.username": KAFKA_USERNAME,
                "sasl.password": KAFKA_PASSWORD,
            }
        )
    if KAFKA_CA and os.path.exists(KAFKA_CA):
        conf["ssl.ca.location"] = KAFKA_CA
    return Consumer(conf)


def _handle_event(evt: dict):
    if bills_collection is None:
        return

    txid = evt.get("transactionId") or evt.get("transaction_id")
    if not txid:
        return

    processed = _processed_coll()
    if processed is not None and processed.find_one({"_id": txid}):
        return

    bill_id = evt.get("billId") or evt.get("bill_id")
    member_id = evt.get("memberId") or evt.get("member_id")
    amount = int(evt.get("amount") or 0)
    raw_status = (evt.get("status") or "").upper()
    failure_reason = evt.get("failureReason") or evt.get("failure_reason")

    if raw_status == "CAPTURED":
        status = "SUCCESS"
    elif raw_status == "FAILED":
        status = "FAILED"
    else:
        if processed is not None:
            processed.insert_one(
                {
                    "_id": txid,
                    "at": datetime.utcnow(),
                    "note": f"unknown_status:{raw_status}",
                }
            )
        return

    try:
        bill_oid = ObjectId(bill_id)
    except Exception:
        if processed is not None:
            processed.insert_one(
                {"_id": txid, "at": datetime.utcnow(), "note": "invalid_bill_id"}
            )
        return

    doc = bills_collection.find_one({"_id": bill_oid})
    if not doc:
        if processed is not None:
            processed.insert_one(
                {"_id": txid, "at": datetime.utcnow(), "note": "bill_not_found"}
            )
        return

    if status == "SUCCESS":
        _apply_payment_success(doc, member_id, amount)
    elif status == "FAILED":
        _save_payment_failure_note(bill_oid, member_id, failure_reason)

    if processed is not None:
        processed.insert_one({"_id": txid, "at": datetime.utcnow()})


def run_consumer():
    c = _build_consumer()
    c.subscribe([TOPIC])

    running = True

    def _stop(*_):
        nonlocal running
        running = False

    signal.signal(signal.SIGINT, _stop)
    signal.signal(signal.SIGTERM, _stop)

    try:
        while running:
            msg = c.poll(1.0)
            if msg is None:
                continue
            if msg.error():
                if msg.error().code() != KafkaError._PARTITION_EOF:
                    raise KafkaException(msg.error())
                continue

            try:
                payload = json.loads(msg.value().decode("utf-8"))
                _handle_event(payload)
                c.commit(msg)
            except Exception as e:
                print(f"[consumer] ERR processing message: {e}", file=sys.stderr)
    finally:
        c.close()


if __name__ == "__main__":
    run_consumer()
