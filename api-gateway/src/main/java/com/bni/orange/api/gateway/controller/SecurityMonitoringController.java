package com.bni.orange.api.gateway.controller;

import com.bni.orange.api.gateway.service.IpBlockingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for security monitoring and IP management.
 * Used for validating and monitoring the multi-layer defense system.
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityMonitoringController {

    private final IpBlockingService ipBlockingService;

    @GetMapping("/ip/{ipAddress}/status")
    public Mono<ResponseEntity<Map<String, Object>>> checkIpStatus(@PathVariable String ipAddress) {
        log.info("Checking status for IP: {}", ipAddress);

        return Mono.zip(
            ipBlockingService.isIpBlocked(ipAddress),
            ipBlockingService.isIpSuspicious(ipAddress),
            ipBlockingService.getViolationCount(ipAddress),
            ipBlockingService.getBlockTimeRemaining(ipAddress)
        ).map(tuple -> {
            boolean isBlocked = tuple.getT1();
            boolean isSuspicious = tuple.getT2();
            long violationCount = tuple.getT3();
            long blockTimeRemaining = tuple.getT4();

            return ResponseEntity.ok(Map.of(
                "ipAddress", ipAddress,
                "blocked", isBlocked,
                "suspicious", isSuspicious,
                "violationCount", violationCount,
                "blockTimeRemainingSeconds", blockTimeRemaining,
                "status", isBlocked ? "BLOCKED" : isSuspicious ? "SUSPICIOUS" : "CLEAN"
            ));
        });
    }

    @DeleteMapping("/ip/{ipAddress}/block")
    public Mono<ResponseEntity<Map<String, Object>>> unblockIp(@PathVariable String ipAddress) {
        log.warn("Manual unblock requested for IP: {}", ipAddress);

        return ipBlockingService.unblockIp(ipAddress)
            .map(success -> {
                if (success) {
                    return ResponseEntity.ok(Map.of(
                        "message", "IP successfully unblocked",
                        "ipAddress", ipAddress,
                        "success", true
                    ));
                } else {
                    return ResponseEntity.ok(Map.of(
                        "message", "IP was not blocked",
                        "ipAddress", ipAddress,
                        "success", false
                    ));
                }
            });
    }

    @PostMapping("/ip/{ipAddress}/block")
    public Mono<ResponseEntity<Map<String, Object>>> blockIp(
            @PathVariable String ipAddress,
            @RequestParam(required = false, defaultValue = "Manual block") String reason) {

        log.warn("Manual block requested for IP: {} - Reason: {}", ipAddress, reason);

        return ipBlockingService.blockIp(ipAddress, reason)
            .then(Mono.just(ResponseEntity.ok(Map.of(
                "message", "IP successfully blocked",
                "ipAddress", ipAddress,
                "reason", reason,
                "success", true
            ))));
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        return Mono.just(ResponseEntity.ok(Map.of(
            "status", "UP",
            "service", "Security Monitoring",
            "timestamp", System.currentTimeMillis()
        )));
    }
}
