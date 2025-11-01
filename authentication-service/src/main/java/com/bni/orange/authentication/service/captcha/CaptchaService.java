package com.bni.orange.authentication.service.captcha;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaService {

    private final CaptchaProperties captchaProperties;
    private final WebClient webClient;

    public Mono<Boolean> validateToken(String token) {
        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("secret", captchaProperties.secret());
        formData.add("response", token);

        return webClient.post()
                .uri(captchaProperties.url())
                .bodyValue(formData)
                .retrieve()
                .bodyToMono(GoogleCaptchaResponse.class)
                .map(GoogleCaptchaResponse::success)
                .doOnNext(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        log.info("Captcha validation successful.");
                    } else {
                        log.warn("Captcha validation failed.");
                    }
                })
                .onErrorResume(ex -> {
                    log.error("Error during captcha validation", ex);
                    return Mono.just(false);
                });
    }
}
