package com.bni.orange.notification.controller;

import com.bni.orange.notification.model.WahaWebhookEvent;
import com.bni.orange.notification.security.WebhookSecurityService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/api/webhooks/waha")
@RequiredArgsConstructor
public class WahaWebhookController {

    private final WebhookSecurityService securityService;
    private final ObjectMapper objectMapper;

    @PostMapping
    public Mono<ResponseEntity<Void>> handleWebhook(
        @RequestBody String rawPayload,
        @RequestHeader(value = "X-Webhook-Hmac", required = false) String signature) {

        log.debug("Received webhook event");

        if (!securityService.verifyWebhookSignature(rawPayload, signature)) {
            log.warn("Invalid webhook signature. Rejecting request.");
            return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
        }

        WahaWebhookEvent event;
        try {
            event = objectMapper.readValue(rawPayload, WahaWebhookEvent.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse webhook payload", e);
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return processWebhookEvent(event)
            .then(Mono.just(ResponseEntity.ok().<Void>build()))
            .onErrorResume(error -> {
                log.error("Error processing webhook event", error);
                return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
            });
    }

    private Mono<Void> processWebhookEvent(WahaWebhookEvent event) {
        log.info("Processing webhook event: {} for session: {}", event.event(), event.session());

        return switch (event.event()) {
            case "session.status" -> handleSessionStatus(event);
            case "message" -> handleIncomingMessage(event);
            case "message.any" -> handleAnyMessage(event);
            case "message.ack" -> handleMessageAck(event);
            case "message.reaction" -> handleMessageReaction(event);
            case "state.change" -> handleStateChange(event);
            default -> {
                log.warn("Unknown webhook event type: {}", event.event());
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleSessionStatus(WahaWebhookEvent event) {
        String status = (String) event.payload().get("status");
        log.info("Session status changed to: {}", status);

        return switch (status) {
            case "WORKING" -> {
                log.info("✅ WhatsApp session is READY. Can send messages now.");
                yield Mono.empty();
            }
            case "SCAN_QR_CODE" -> {
                log.warn("⚠️ WhatsApp session requires QR code scan");
                yield Mono.empty();
            }
            case "FAILED" -> {
                log.error("❌ WhatsApp session FAILED. Manual intervention required.");
                yield Mono.empty();
            }
            case "STARTING" -> {
                log.info("🔄 WhatsApp session is starting...");
                yield Mono.empty();
            }
            case "STOPPED" -> {
                log.warn("⏸️ WhatsApp session is stopped");
                yield Mono.empty();
            }
            default -> {
                log.warn("Unknown session status: {}", status);
                yield Mono.empty();
            }
        };
    }

    private Mono<Void> handleIncomingMessage(WahaWebhookEvent event) {
        log.info("Received incoming message: {}", event.payload());
        // TODO: Implement incoming message handling if needed
        // For OTP use case, we typically only send messages, not receive them
        return Mono.empty();
    }

    private Mono<Void> handleAnyMessage(WahaWebhookEvent event) {
        log.debug("Received any message event: {}", event.payload());
        return Mono.empty();
    }

    private Mono<Void> handleMessageAck(WahaWebhookEvent event) {
        var ackStatus = event.payload().get("ack");
        var messageId = event.payload().get("id");

        log.info("Message acknowledgment received. Message ID: {}, ACK: {}", messageId, ackStatus);

        // ACK statuses:
        // - SERVER: Message sent to WhatsApp server
        // - DEVICE: Message delivered to recipient's device
        // - READ: Message read by recipient
        // - PLAYED: Voice/video message played by recipient

        return Mono.empty();
    }

    private Mono<Void> handleMessageReaction(WahaWebhookEvent event) {
        log.debug("Received message reaction: {}", event.payload());
        return Mono.empty();
    }

    private Mono<Void> handleStateChange(WahaWebhookEvent event) {
        String state = (String) event.payload().get("state");
        log.info("State changed to: {}", state);
        return Mono.empty();
    }
}
