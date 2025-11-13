package com.bni.orange.users.controller;

import com.bni.orange.users.model.response.ApiResponse;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.service.UserQueryService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserQueryService userQueryService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUserProfile(
        @AuthenticationPrincipal Jwt jwt
    ) {
        return ResponseEntity.ok(ApiResponse.success(userQueryService.getCurrentUserProfile(UUID.fromString(jwt.getSubject()))));
    }

    /**
     * Find user by phone number
     * Used by transaction-service for recipient lookup
     *
     * @param phoneNumber Phone number in E.164 format (e.g., +628126754912)
     * @return User profile information
     */
    @GetMapping("/by-phone")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<UserProfileResponse>> findByPhoneNumber(
        @RequestParam("phone") @NotBlank String phoneNumber
    ) {
        log.debug("Finding user by phone number: {}", phoneNumber);
        var userProfile = userQueryService.findByPhoneNumber(phoneNumber);
        return ResponseEntity.ok(ApiResponse.success(userProfile));
    }
}
