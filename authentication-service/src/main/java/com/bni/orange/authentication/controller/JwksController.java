package com.bni.orange.authentication.controller;

import com.nimbusds.jose.jwk.JWKSet;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JWKSet jwkSet;

    @GetMapping("/oauth2/jwks")
    public Map<String, Object> keys() {
        return this.jwkSet.toJSONObject();
    }
}