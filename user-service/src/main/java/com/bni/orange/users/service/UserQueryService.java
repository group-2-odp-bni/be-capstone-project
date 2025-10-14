package com.bni.orange.users.service;

import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.repository.UserProfileViewRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserProfileViewRepository userProfileViewRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentUserProfile(UUID userId) {
        log.debug("Fetching profile for user ID: {}", userId);

        var userProfile = userProfileViewRepository.findById(userId)
            .orElseThrow(() -> {
                log.warn("User profile not found for ID: {}", userId);
                return new BusinessException(ErrorCode.USER_NOT_FOUND);
            });

        return UserProfileResponse.builder()
            .id(userProfile.getId())
            .name(userProfile.getName())
            .email(userProfile.getEmail())
            .phoneNumber(userProfile.getPhoneNumber())
            .profileImageUrl(userProfile.getProfileImageUrl())
            .emailVerified(userProfile.getEmailVerified())
            .phoneVerified(userProfile.getPhoneVerified())
            .status(userProfile.getStatus())
            .lastLoginAt(userProfile.getLastLoginAt())
            .build();
    }
}
