package com.bni.orange.authentication.controller;

import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.request.PinRequest;
import com.bni.orange.authentication.model.request.RefreshTokenRequest;
import com.bni.orange.authentication.model.response.MessageResponse;
import com.bni.orange.authentication.model.response.StateTokenResponse;
import com.bni.orange.authentication.model.response.TokenResponse;
import com.bni.orange.authentication.service.AuthFlowService;
import com.bni.orange.authentication.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthFlowService authFlowService;
    private final TokenService tokenService;

    @PostMapping("/request")
    public ResponseEntity<MessageResponse> requestOtp(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authFlowService.requestOtp(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<StateTokenResponse> verifyOtp(@RequestBody @Valid OtpVerifyRequest request) {
        return ResponseEntity.ok(authFlowService.verifyOtp(request));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<MessageResponse> resendOtp(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authFlowService.requestOtp(request));
    }

    @PostMapping("/pin")
    @PreAuthorize("hasAuthority('SCOPE_PIN_SETUP') or hasAuthority('SCOPE_PIN_LOGIN')")
    public ResponseEntity<TokenResponse> authenticateWithPin(
        Authentication authentication,
        @RequestBody @Valid PinRequest request,
        HttpServletRequest servletRequest
    ) {
        var jwt = (Jwt) authentication.getPrincipal();
        var userId = UUID.fromString(jwt.getSubject());
        var scope = jwt.getClaimAsString("scope");
        var jti = jwt.getId();

        var tokenResponse = authFlowService.authenticateWithPin(userId, request.pin(), scope, jti, servletRequest);

        return ResponseEntity.ok(tokenResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refreshToken(@RequestBody @Valid RefreshTokenRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(tokenService.rotateRefreshToken(request.refreshToken(), servletRequest));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<MessageResponse> logout(Authentication authentication, @RequestBody @Valid RefreshTokenRequest request) {
        var jwt = (Jwt) authentication.getPrincipal();
        tokenService.logout(jwt, request.refreshToken());
        return ResponseEntity.ok(new MessageResponse("Successfully logged out"));
    }
}