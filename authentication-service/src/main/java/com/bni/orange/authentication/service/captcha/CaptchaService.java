package com.bni.orange.authentication.service.captcha;

import com.bni.orange.authentication.config.properties.CaptchaProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.response.GoogleCaptchaResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaService {

    private final CaptchaProperties captchaProperties;
    private final WebClient webClient;
    private final CaptchaAttemptService captchaAttemptService;

    public Mono<Boolean> validateToken(String token, String expectedAction) {
        if (!captchaProperties.enabled()) {
            log.warn("Captcha validation is disabled. Returning true by default.");
            return Mono.just(true);
        }

        return captchaAttemptService.isTokenAlreadyUsed(token)
            .flatMap(isUsed -> {
                if (isUsed) {
                    log.warn("Captcha token reuse detected: {}", token);
                    return Mono.error(new BusinessException(ErrorCode.INVALID_CAPTCHA, "Token reuse detected."));
                }
                return verifyWithGoogle(token, expectedAction);
            });
    }

    private Mono<Boolean> verifyWithGoogle(String token, String expectedAction) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("secret", captchaProperties.secret());
        formData.add("response", token);

        return webClient.post()
            .uri(captchaProperties.url())
            .bodyValue(formData)
            .retrieve()
            .bodyToMono(GoogleCaptchaResponse.class)
            .flatMap(response -> {
                if (!response.success()) {
                    log.warn("Captcha validation failed with error codes: {}", response.errorCodes());
                    return Mono.just(false);
                }
                if (!expectedAction.equals(response.action())) {
                    log.warn("Captcha action mismatch. Expected: {}, Actual: {}", expectedAction, response.action());
                    return Mono.just(false);
                }
                if (response.score() < captchaProperties.scoreThreshold()) {
                    log.warn("Captcha score too low. Score: {}, Threshold: {}", response.score(), captchaProperties.scoreThreshold());
                    return Mono.just(false);
                }
                if (!captchaProperties.hostname().equals(response.hostname())) {
                    log.warn("Captcha hostname mismatch. Expected: {}, Actual: {}", captchaProperties.hostname(), response.hostname());
                    return Mono.just(false);
                }
                if (isTimestampInvalid(response.challengeTs())) {
                    log.warn("Captcha timestamp is too old.");
                    return Mono.just(false);
                }

                log.info("Captcha validation successful for action '{}' with score {}", response.action(), response.score());
                return Mono.just(true);
            })
            .onErrorResume(ex -> {
                log.error("Error during captcha validation with Google", ex);
                return Mono.just(false);
            });
    }

    private boolean isTimestampInvalid(String timestamp) {
        try {
            Instant challengeTime = Instant.parse(timestamp);
            return challengeTime.isBefore(Instant.now().minusSeconds(120));
        } catch (Exception e) {
            log.error("Could not parse captcha timestamp: {}", timestamp, e);
            return true;
        }
    }
}
