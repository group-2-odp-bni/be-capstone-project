package com.bni.orange.authentication.controller;

import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.request.PinChangeRequest;
import com.bni.orange.authentication.model.request.PinResetConfirmRequest;
import com.bni.orange.authentication.model.response.MessageResponse;
import com.bni.orange.authentication.model.response.StateTokenResponse;
import com.bni.orange.authentication.service.AuthFlowService;
import com.bni.orange.authentication.service.PinService;
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
@RequestMapping("/api/v1/pin")
public class PinController {

    private final PinService pinService;
    private final AuthFlowService authFlowService;

    @PostMapping("/change")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<MessageResponse> changePin(Authentication authentication, @RequestBody @Valid PinChangeRequest request) {
        var userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(pinService.changePin(userId, request));
    }

    @PostMapping("/reset/request")
    public ResponseEntity<MessageResponse> requestPinReset(@RequestBody @Valid AuthRequest request) {
        return ResponseEntity.ok(authFlowService.requestOtp(request));
    }


    @PostMapping("/reset/verify")
    public ResponseEntity<StateTokenResponse> verifyPinResetOtp(@RequestBody @Valid OtpVerifyRequest request) {
        return ResponseEntity.ok(authFlowService.verifyOtp(request, "RESET"));
    }

    @PostMapping("/reset/confirm")
    @PreAuthorize("hasAuthority('SCOPE_PIN_RESET')")
    public ResponseEntity<MessageResponse> confirmPinReset(Authentication authentication, @RequestBody @Valid PinResetConfirmRequest request) {
        var jwt = (Jwt) authentication.getPrincipal();
        var userId = UUID.fromString(jwt.getSubject());
        return ResponseEntity.ok(pinService.confirmPinReset(userId, request));
    }
}