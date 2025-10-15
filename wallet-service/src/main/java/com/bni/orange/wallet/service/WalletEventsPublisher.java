package com.bni.orange.wallet.service;

import com.bni.orange.wallet.model.entity.Wallet;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@ConditionalOnProperty(
        prefix = "wallet.events",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true   
)
@Component
public class WalletEventsPublisher {

    private static final Logger log = LoggerFactory.getLogger(WalletEventsPublisher.class);

    private final KafkaTemplate<String, Object> kafka;

    public WalletEventsPublisher(KafkaTemplate<String, Object> kafka) {
        this.kafka = kafka;
    }

    public void walletCreated(Wallet w, String xReq, String xCorr) {
        String topic = "wallet.created.v1";
        String key   = w.getId().toString();
        var payload  = new WalletCreatedPayload(
                w.getId().toString(),
                w.getUserId().toString(),
                w.getCurrency(),
                w.getStatus().name(),
                w.getCreatedAt().toString()
        );
        ProducerRecord<String, Object> rec = new ProducerRecord<>(topic, key, payload);
        addTraceHeaders(rec, xReq, xCorr);

        try {
            kafka.send(rec).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("Kafka send failed for {} key={}: {}", topic, key, ex.toString());
                } else if (log.isDebugEnabled()) {
                    log.debug("Kafka sent {} key={} to {}", topic, key, res.getRecordMetadata());
                }
            });
        } catch (Exception ex) {
            log.warn("Kafka send threw synchronously for {} key={}: {}", topic, key, ex.toString());
        }
    }

    public void balanceAdjusted(Wallet w, BigDecimal amount, String reason, String xReq, String xCorr) {
        String topic = "wallet.balance.adjusted.v1";
        String key   = w.getId().toString();
        var payload  = new BalanceAdjustedPayload(
                w.getId().toString(),
                w.getCurrency(),
                amount.toPlainString(),
                w.getBalanceSnapshot().toPlainString(),
                reason
        );
        ProducerRecord<String, Object> rec = new ProducerRecord<>(topic, key, payload);
        addTraceHeaders(rec, xReq, xCorr);

        try {
            kafka.send(rec).whenComplete((res, ex) -> {
                if (ex != null) {
                    log.warn("Kafka send failed for {} key={}: {}", topic, key, ex.toString());
                } else if (log.isDebugEnabled()) {
                    log.debug("Kafka sent {} key={} to {}", topic, key, res.getRecordMetadata());
                }
            });
        } catch (Exception ex) {
            log.warn("Kafka send threw synchronously for {} key={}: {}", topic, key, ex.toString());
        }
    }

    private static void addTraceHeaders(ProducerRecord<String, Object> rec, String xReq, String xCorr) {
        if (xReq != null)  rec.headers().add(new RecordHeader("x-request-id", xReq.getBytes(StandardCharsets.UTF_8)));
        if (xCorr != null) rec.headers().add(new RecordHeader("x-correlation-id", xCorr.getBytes(StandardCharsets.UTF_8)));
        rec.headers().add(new RecordHeader("x-producer", "wallet-service".getBytes(StandardCharsets.UTF_8)));
        rec.headers().add(new RecordHeader("content-type", "application/json".getBytes(StandardCharsets.UTF_8)));
    }

    public record WalletCreatedPayload(String wallet_id, String user_id, String currency, String status, String created_at) {}
    public record BalanceAdjustedPayload(String wallet_id, String currency, String amount, String balance_after, String reason) {}
}
