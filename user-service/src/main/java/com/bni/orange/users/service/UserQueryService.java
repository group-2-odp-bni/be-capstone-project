package com.bni.orange.users.service;

import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserProfileRepository userProfileRepository;
    private final FileStorageService fileStorageService;

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        log.debug("Fetching profile for user ID: {}", userId);

        var userProfile = userProfileRepository
            .findById(userId)
            .orElseThrow(() -> {
                log.warn("User profile not found for ID: {}", userId);
                return new BusinessException(ErrorCode.USER_NOT_FOUND);
            });

        String profileImageUrl = null;
        if (userProfile.getProfileImageUrl() != null && !userProfile.getProfileImageUrl().isBlank()) {
            try {
                profileImageUrl = fileStorageService.generateSignedUrl(userProfile.getProfileImageUrl());
                log.debug("Generated signed URL for user {} profile image", userId);
            } catch (Exception e) {
                log.error("Failed to generate signed URL for user {} profile image. Returning null.", userId, e);
            }
        }

        return UserProfileResponse.builder()
            .id(userProfile.getId())
            .name(userProfile.getName())
            .email(userProfile.getEmail())
            .phoneNumber(userProfile.getPhoneNumber())
            .bio(userProfile.getBio())
            .address(userProfile.getAddress())
            .dateOfBirth(userProfile.getDateOfBirth())
            .profileImageUrl(profileImageUrl) // Fresh signed URL with 60-min expiry
            .emailVerified(userProfile.hasVerifiedEmail())
            .phoneVerified(userProfile.hasVerifiedPhone())
            .emailVerifiedAt(userProfile.getEmailVerifiedAt())
            .phoneVerifiedAt(userProfile.getPhoneVerifiedAt())
            .build();
    }
}
