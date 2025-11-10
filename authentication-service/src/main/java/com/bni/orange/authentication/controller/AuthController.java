package com.bni.orange.authentication.controller;

import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.request.PinRequest;
import com.bni.orange.authentication.model.request.RefreshTokenRequest;
import com.bni.orange.authentication.model.response.ApiResponse;
import com.bni.orange.authentication.model.response.OtpResponse;
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
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthFlowService authFlowService;
    private final TokenService tokenService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<OtpResponse>> requestLoginOtp(
        @RequestBody @Valid AuthRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authFlowService.requestLoginOtp(request, servletRequest));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<OtpResponse>> requestRegistrationOtp(
        @RequestBody @Valid AuthRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authFlowService.requestRegistrationOtp(request, servletRequest));
    }

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<StateTokenResponse>> verifyOtp(
        @RequestBody @Valid OtpVerifyRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authFlowService.verifyOtp(request, servletRequest));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<OtpResponse>> resendOtp(
        @RequestBody @Valid AuthRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authFlowService.requestLoginOtp(request, servletRequest));
    }

    @PostMapping("/pin")
    @PreAuthorize("hasAuthority('SCOPE_PIN_SETUP') or hasAuthority('SCOPE_PIN_LOGIN')")
    public ResponseEntity<ApiResponse<TokenResponse>> authenticateWithPin(
        Authentication authentication,
        @RequestBody @Valid PinRequest request,
        HttpServletRequest servletRequest
    ) {
        var jwt = (Jwt) authentication.getPrincipal();
        var userId = UUID.fromString(jwt.getSubject());
        var scope = jwt.getClaimAsString("scope");
        var jti = jwt.getId();

        return ResponseEntity.ok(authFlowService.authenticateWithPin(userId, request.pin(), scope, jti, servletRequest));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(
        @RequestBody @Valid RefreshTokenRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(tokenService.rotateRefreshToken(request.refreshToken(), servletRequest));
    }

    @PostMapping("/logout")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> logout(
        Authentication authentication,
        @RequestBody @Valid RefreshTokenRequest request,
        HttpServletRequest servletRequest
    ) {
        var jwt = (Jwt) authentication.getPrincipal();
        return ResponseEntity.ok(tokenService.logout(jwt, request.refreshToken(), servletRequest));
    }
}