package com.bni.orange.users.controller;

import com.bni.orange.users.model.request.UpdateProfileRequest;
import com.bni.orange.users.model.request.VerifyOtpRequest;
import com.bni.orange.users.model.response.ApiResponse;
import com.bni.orange.users.model.response.ProfileImageUploadResponse;
import com.bni.orange.users.model.response.ProfileUpdateResponse;
import com.bni.orange.users.model.response.VerificationResponse;
import org.springframework.web.multipart.MultipartFile;
import com.bni.orange.users.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users/profile")
public class ProfileController {

    private final ProfileService profileService;

    @PutMapping
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<ProfileUpdateResponse>> updateProfile(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody UpdateProfileRequest request
    ) {
        var response = profileService.updateProfile(UUID.fromString(jwt.getSubject()), request);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }

    @PostMapping("/verify-email")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<VerificationResponse>> verifyEmail(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody VerifyOtpRequest request
    ) {
        var response = profileService.verifyEmail(UUID.fromString(jwt.getSubject()), request.getOtpCode());
        return ResponseEntity.ok(ApiResponse.success(response, "Email verified successfully"));
    }

    @PostMapping("/verify-phone")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<VerificationResponse>> verifyPhone(
        @AuthenticationPrincipal Jwt jwt,
        @Valid @RequestBody VerifyOtpRequest request
    ) {
        var response = profileService.verifyPhone(UUID.fromString(jwt.getSubject()), request.getOtpCode());
        return ResponseEntity.ok(ApiResponse.success(response, "Phone number verified successfully"));
    }

    @PostMapping("/upload-image")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<ProfileImageUploadResponse>> uploadProfileImage(
        @AuthenticationPrincipal Jwt jwt,
        @RequestParam("file") MultipartFile file
    ) {
        var response = profileService.uploadProfileImage(UUID.fromString(jwt.getSubject()), file);
        return ResponseEntity.ok(ApiResponse.success(response, response.getMessage()));
    }
}
