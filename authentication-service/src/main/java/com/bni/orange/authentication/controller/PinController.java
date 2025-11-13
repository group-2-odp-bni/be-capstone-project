package com.bni.orange.authentication.controller;

import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.request.PinChangeRequest;
import com.bni.orange.authentication.model.request.PinResetConfirmRequest;
import com.bni.orange.authentication.model.request.PinVerifyRequest;
import com.bni.orange.authentication.model.response.ApiResponse;
import com.bni.orange.authentication.model.response.OtpResponse;
import com.bni.orange.authentication.model.response.PinVerifyResponse;
import com.bni.orange.authentication.model.response.StateTokenResponse;
import com.bni.orange.authentication.service.AuthFlowService;
import com.bni.orange.authentication.service.PinService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/pin")
public class PinController {

    private final PinService pinService;
    private final AuthFlowService authFlowService;

    @PostMapping("/change")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> changePin(
        Authentication authentication,
        @RequestBody @Valid PinChangeRequest request,
        HttpServletRequest servletRequest
    ) {
        var userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(pinService.changePin(userId, request, servletRequest));
    }

    @PostMapping("/reset/request")
    public ResponseEntity<ApiResponse<OtpResponse>> requestPinReset(
        @RequestBody @Valid AuthRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authFlowService.requestOtp(request, servletRequest));
    }

    @PostMapping("/reset/verify")
    public ResponseEntity<ApiResponse<StateTokenResponse>> verifyPinResetOtp(
        @RequestBody @Valid OtpVerifyRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(authFlowService.verifyOtp(request, "RESET", servletRequest));
    }

    @PostMapping("/reset/confirm")
    @PreAuthorize("hasAuthority('SCOPE_PIN_RESET')")
    public ResponseEntity<ApiResponse<Void>> confirmPinReset(
        Authentication authentication,
        @RequestBody @Valid PinResetConfirmRequest request,
        HttpServletRequest servletRequest
    ) {
        var jwt = (Jwt) authentication.getPrincipal();
        var userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(pinService.confirmPinReset(userId, request, servletRequest));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<PinVerifyResponse>> verifyPin(
        Authentication authentication,
        @RequestBody @Valid PinVerifyRequest request,
        HttpServletRequest servletRequest
    ) {
        return ResponseEntity.ok(pinService.verifyPin(UUID.fromString(authentication.getName()), request, servletRequest));
    }
}
