package com.bni.orange.notification.component;

import com.bni.orange.notification.service.WahaSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
    name = "orange.waha-client.health-monitor.enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class WahaSessionHealthMonitor {

    private final WahaSessionService sessionService;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger totalRecoveryAttempts = new AtomicInteger(0);

    @Scheduled(
        fixedDelayString = "${orange.waha-client.health-monitor.interval:300000}",
        initialDelayString = "${orange.waha-client.health-monitor.initial-delay:60000}"
    )
    public void monitorSessionHealth() {
        log.debug("🔍 Performing WAHA session health check...");

        sessionService.isSessionReady()
            .timeout(Duration.ofSeconds(10))
            .flatMap(isReady -> {
                if (isReady) {
                    if (consecutiveFailures.get() > 0) {
                        log.info("✅ WAHA session recovered after {} consecutive failures",
                            consecutiveFailures.get());
                    }
                    consecutiveFailures.set(0);
                    log.debug("✅ WAHA session healthy (status: WORKING)");
                    return Mono.empty();
                }

                var failures = consecutiveFailures.incrementAndGet();
                var recoveryCount = totalRecoveryAttempts.incrementAndGet();

                log.warn("⚠️ WAHA session not ready (consecutive failures: {}, total recovery attempts: {})",
                    failures, recoveryCount);

                if (failures >= 3) {
                    log.error("❌ CRITICAL: WAHA session failed {} times consecutively. Manual intervention may be required.",
                        failures);
                }

                log.info("🔄 Attempting auto-recovery #{}", recoveryCount);
                return sessionService.startSession();
            })
            .doOnError(error -> {
                int failures = consecutiveFailures.incrementAndGet();
                log.error("❌ WAHA health check failed (consecutive failures: {}): {}",
                    failures, error.getMessage());
            })
            .onErrorResume(error -> Mono.empty())
            .subscribe();
    }

    public HealthMetrics getMetrics() {
        return new HealthMetrics(
            consecutiveFailures.get(),
            totalRecoveryAttempts.get()
        );
    }

    public record HealthMetrics(
        int consecutiveFailures,
        int totalRecoveryAttempts
    ) {}
}
