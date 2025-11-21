package com.bni.orange.users.controller;

import com.bni.orange.users.model.response.ApiResponse;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.service.InternalUserService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/internal/v1/user")
public class InternalUserController {

    private final InternalUserService internalUserService;

    @GetMapping("/by-phone")
    public ResponseEntity<ApiResponse<UserProfileResponse>> findByPhoneNumber(
        @RequestParam("phone") @NotBlank String phoneNumber
    ) {
        log.debug("Finding user by phone number: {}", phoneNumber);
        var userProfile = internalUserService.findByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(ApiResponse.success(userProfile));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> findById(
        @PathVariable UUID id
    ) {
        log.debug("Finding user by ID: {}", id);
        var userProfile = internalUserService.findById(id);
        return ResponseEntity.ok(ApiResponse.success(userProfile));
    }
}
