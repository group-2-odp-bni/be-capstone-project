package com.bni.orange.notification.component;

import com.bni.orange.notification.service.WahaSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class WahaSessionInitializer implements ApplicationRunner {

    private final WahaSessionService sessionService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 Initializing WhatsApp session on startup...");

        sessionService.getSessionStatus()
            .timeout(Duration.ofSeconds(10))
            .flatMap(status -> {
                var currentStatus = status.status();
                log.info("📊 Current WAHA session status: {}", currentStatus);

                if ("WORKING".equalsIgnoreCase(currentStatus)) {
                    log.info("✅ WAHA session already WORKING. No action needed.");
                    return Mono.empty();
                }

                if ("FAILED".equalsIgnoreCase(currentStatus)) {
                    log.warn("⚠️ WAHA session in FAILED state. Attempting restart...");
                    return sessionService.startSession();
                }

                log.warn("⚠️ WAHA session status: {}. Auto-starting...", currentStatus);
                return sessionService.startSession();
            })
            .onErrorResume(error -> {
                log.warn("⚠️ Could not check WAHA session status ({}). Attempting fresh start...",
                    error.getMessage());
                return sessionService.startSession();
            })
            .doOnSuccess(v ->
                log.info("✅ WhatsApp session initialization completed successfully")
            )
            .doOnError(error ->
                log.error("❌ Failed to initialize WhatsApp session. Health monitor will retry.", error)
            )
            .subscribe();
    }
}
