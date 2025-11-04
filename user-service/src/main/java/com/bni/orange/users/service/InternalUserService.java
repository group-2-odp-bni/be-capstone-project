package com.bni.orange.users.service;

import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.repository.UserProfileRepository;
import com.bni.orange.users.util.PhoneNumberUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Log4j2
@Service
@RequiredArgsConstructor
public class InternalUserService {

    private final UserProfileRepository userProfileRepository;

    @Transactional(readOnly = true)
    public UserProfileResponse findByPhoneNumber(String phoneNumber) {
        log.debug("Fetching profile for phone number: {}", phoneNumber);

        var normalizedPhone = PhoneNumberUtils.normalize(phoneNumber);

        var userProfile = userProfileRepository
            .findByPhoneNumber(normalizedPhone)
            .orElseThrow(() -> {
                log.warn("User profile not found for phone number: {}", normalizedPhone);
                return new BusinessException(ErrorCode.USER_NOT_FOUND);
            });

        return mapToResponse(userProfile);
    }

    @Transactional(readOnly = true)
    public UserProfileResponse findById(UUID uuid) {
        log.debug("Fetching profile for ID: {}", uuid);

        var userProfile = userProfileRepository.findById(uuid)
            .orElseThrow(() -> {
                log.warn("User profile not found for ID: {}", uuid);
                return new BusinessException(ErrorCode.USER_NOT_FOUND);
            });

        return mapToResponse(userProfile);
    }


    private UserProfileResponse mapToResponse(com.bni.orange.users.model.entity.UserProfile userProfile) {
        return UserProfileResponse.builder()
            .id(userProfile.getId())
            .name(userProfile.getName())
            .email(userProfile.getEmail())
            .phoneNumber(userProfile.getPhoneNumber())
            .bio(userProfile.getBio())
            .address(userProfile.getAddress())
            .dateOfBirth(userProfile.getDateOfBirth())
            .profileImageUrl(userProfile.getProfileImageUrl())
            .emailVerified(userProfile.hasVerifiedEmail())
            .phoneVerified(userProfile.hasVerifiedPhone())
            .emailVerifiedAt(userProfile.getEmailVerifiedAt())
            .phoneVerifiedAt(userProfile.getPhoneVerifiedAt())
            .build();
    }
}
