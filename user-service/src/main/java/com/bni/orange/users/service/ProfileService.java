package com.bni.orange.users.service;

import com.bni.orange.users.config.properties.GcsProperties;
import com.bni.orange.users.config.properties.KafkaTopicProperties;
import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.event.EventPublisher;
import com.bni.orange.users.event.ProfileEventFactory;
import com.bni.orange.users.model.entity.UserProfile;
import com.bni.orange.users.model.enums.TokenType;
import com.bni.orange.users.model.request.UpdateProfileRequest;
import com.bni.orange.users.model.response.ProfileImageUploadResponse;
import com.bni.orange.users.model.response.ProfileUpdateResponse;
import com.bni.orange.users.model.response.VerificationResponse;
import org.springframework.web.multipart.MultipartFile;
import com.bni.orange.users.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserProfileRepository profileRepository;
    private final VerificationService verificationService;
    private final EventPublisher eventPublisher;
    private final KafkaTopicProperties topicProperties;
    private final FileStorageService fileStorageService;
    private final GcsProperties gcsProperties;

    @Transactional
    public ProfileUpdateResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        if (request.isEmpty()) {
            throw new BusinessException(ErrorCode.PROFILE_UPDATE_FAILED, "No fields provided for update");
        }

        var profile = profileRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var updatedFields = new ArrayList<String>();
        var pendingVerifications = new HashMap<String, ProfileUpdateResponse.PendingVerification>();

        if (Objects.nonNull(request.getName()) && !request.getName().equals(profile.getName())) {
            profile.setName(request.getName());
            updatedFields.add("name");
            publishNameUpdatedEvent(profile);
        }

        if (Objects.nonNull(request.getEmail())) {
            handleEmailUpdate(userId, profile, request.getEmail(), pendingVerifications);
        }

        if (Objects.nonNull(request.getPhoneNumber())) {
            handlePhoneUpdate(userId, profile, request.getPhoneNumber(), pendingVerifications);
        }

        profileRepository.save(profile);

        return ProfileUpdateResponse.builder()
            .message(buildResponseMessage(updatedFields, pendingVerifications))
            .updatedFields(updatedFields.isEmpty() ? null : updatedFields)
            .pendingVerifications(pendingVerifications.isEmpty() ? null : pendingVerifications)
            .build();
    }

    private void handleEmailUpdate(
        UUID userId,
        UserProfile profile,
        String newEmail,
        HashMap<String, ProfileUpdateResponse.PendingVerification> pendingVerifications
    ) {
        if (newEmail.equals(profile.getEmail())) {
            throw new BusinessException(ErrorCode.SAME_VALUE_UPDATE, "New email is the same as current email");
        }

        if (profileRepository.existsByEmailAndIdNot(newEmail, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL, "Email address is already in use");
        }

        if (profileRepository.isPendingEmailAlreadyInUse(newEmail, userId)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_PENDING, "This email is already pending verification");
        }

        profile.setPendingEmail(newEmail);
        verificationService.generateEmailOtp(userId, newEmail);

        pendingVerifications.put("email", ProfileUpdateResponse.PendingVerification.builder()
            .field("email")
            .value(newEmail)
            .otpSent(true)
            .expiresInSeconds(300L)
            .verifyEndpoint("/api/v1/users/profile/verify-email")
            .build());

        log.info("Email update initiated for user: {}. OTP sent to: {}", userId, newEmail);
    }

    private void handlePhoneUpdate(
        UUID userId,
        UserProfile profile,
        String newPhone,
        HashMap<String, ProfileUpdateResponse.PendingVerification> pendingVerifications
    ) {
        if (newPhone.equals(profile.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.SAME_VALUE_UPDATE, "New phone number is the same as current");
        }

        if (profileRepository.existsByPhoneNumberAndIdNot(newPhone, userId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_PHONE, "Phone number is already in use");
        }

        if (profileRepository.isPendingPhoneAlreadyInUse(newPhone, userId)) {
            throw new BusinessException(ErrorCode.PHONE_ALREADY_PENDING, "This phone number is already pending verification");
        }

        profile.setPendingPhone(newPhone);
        verificationService.generatePhoneOtp(userId, newPhone);

        pendingVerifications.put("phone", ProfileUpdateResponse.PendingVerification.builder()
            .field("phoneNumber")
            .value(newPhone)
            .otpSent(true)
            .expiresInSeconds(300L)
            .verifyEndpoint("/api/v1/users/profile/verify-phone")
            .build());

        log.info("Phone update initiated for user: {}. OTP sent to: {}", userId, newPhone);
    }

    @Transactional
       public VerificationResponse verifyEmail(UUID userId, String otpCode) {
        log.info("Verifying email OTP for user: {}", userId);
        var profile = profileRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (profile.getPendingEmail() == null) {
            throw new BusinessException(ErrorCode.NO_PENDING_VERIFICATION, "No pending email verification found");
        }

        var verifiedEmail = verificationService.verifyOtp(userId, TokenType.EMAIL, otpCode);
        if (!verifiedEmail.equals(profile.getPendingEmail())) {
            throw new BusinessException(ErrorCode.VERIFICATION_MISMATCH, "Verification token does not match pending email");
        }

        profile.applyVerifiedEmail(verifiedEmail);
        profileRepository.save(profile);

        publishEmailVerifiedEvent(profile);
        log.info("Email verified and updated for user: {}. New email: {}", userId, verifiedEmail);
        return VerificationResponse.success("email", verifiedEmail);
    }

    @Transactional
    public VerificationResponse verifyPhone(UUID userId, String otpCode) {
        log.info("Verifying phone OTP for user: {}", userId);

        var profile = profileRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if (profile.getPendingPhone() == null) {
            throw new BusinessException(ErrorCode.NO_PENDING_VERIFICATION, "No pending phone verification found");
        }

        var verifiedPhone = verificationService.verifyOtp(userId, TokenType.PHONE, otpCode);
        if (!verifiedPhone.equals(profile.getPendingPhone())) {
            throw new BusinessException(ErrorCode.VERIFICATION_MISMATCH, "Verification token does not match pending phone");
        }
        profile.applyVerifiedPhone(verifiedPhone);
        profileRepository.save(profile);

        publishPhoneVerifiedEvent(profile);
        log.info("Phone verified and updated for user: {}. New phone: {}", userId, verifiedPhone);
        return VerificationResponse.success("phoneNumber", verifiedPhone);
    }

    @Transactional(readOnly = true)
    public ProfileUpdateResponse.PendingVerification resendEmailOtp(UUID userId) {
        log.info("Resending email OTP for user: {}", userId);

        var profile = profileRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (profile.getPendingEmail() == null) {
            throw new BusinessException(ErrorCode.NO_PENDING_VERIFICATION, "No pending email verification found");
        }

        verificationService.generateEmailOtp(userId, profile.getPendingEmail());

        log.info("Email OTP resent successfully for user: {}. OTP sent to: {}", userId, profile.getPendingEmail());

        return ProfileUpdateResponse.PendingVerification.builder()
            .field("email")
            .value(profile.getPendingEmail())
            .otpSent(true)
            .expiresInSeconds(300L)
            .verifyEndpoint("/api/v1/users/profile/verify-email")
            .build();
    }

    @Transactional(readOnly = true)
    public ProfileUpdateResponse.PendingVerification resendPhoneOtp(UUID userId) {
        log.info("Resending phone OTP for user: {}", userId);

        var profile = profileRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (profile.getPendingPhone() == null) {
            throw new BusinessException(ErrorCode.NO_PENDING_VERIFICATION, "No pending phone verification found");
        }

        verificationService.generatePhoneOtp(userId, profile.getPendingPhone());

        log.info("Phone OTP resent successfully for user: {}. OTP sent to: {}", userId, profile.getPendingPhone());

        return ProfileUpdateResponse.PendingVerification.builder()
            .field("phoneNumber")
            .value(profile.getPendingPhone())
            .otpSent(true)
            .expiresInSeconds(300L)
            .verifyEndpoint("/api/v1/users/profile/verify-phone")
            .build();
    }

    private String buildResponseMessage(
        ArrayList<String> updatedFields,
        HashMap<String, ProfileUpdateResponse.PendingVerification> pendingVerifications
    ) {
        if (updatedFields.isEmpty() && pendingVerifications.isEmpty()) {
            return "No changes made";
        }

        if (!updatedFields.isEmpty() && pendingVerifications.isEmpty()) {
            return "Profile updated successfully";
        }

        if (updatedFields.isEmpty()) {
            return "Verification required. OTP sent to your email/phone";
        }

        return "Profile partially updated. Please verify your email/phone to complete the update";
    }

    private void publishEmailVerifiedEvent(UserProfile profile) {
        try {
            var topicName = topicProperties.definitions().get("profile-email-verified").name();
            var event = ProfileEventFactory.createEmailVerifiedEvent(profile);
            eventPublisher.publish(topicName, profile.getId().toString(), event);
            log.debug("Email verified event published for user: {}", profile.getId());
        } catch (Exception e) {
            log.error("Failed to publish email verified event for user: {}", profile.getId(), e);
        }
    }

    private void publishPhoneVerifiedEvent(UserProfile profile) {
        try {
            var topicName = topicProperties.definitions().get("profile-phone-verified").name();
            var event = ProfileEventFactory.createPhoneVerifiedEvent(profile);
            eventPublisher.publish(topicName, profile.getId().toString(), event);
            log.debug("Phone verified event published for user: {}", profile.getId());
        } catch (Exception e) {
            log.error("Failed to publish phone verified event for user: {}", profile.getId(), e);
        }
    }

    private void publishNameUpdatedEvent(UserProfile profile) {
        try {
            var topicName = topicProperties.definitions().get("profile-name-updated").name();
            var event = ProfileEventFactory.createNameUpdatedEvent(profile);
            eventPublisher.publish(topicName, profile.getId().toString(), event);
            log.debug("Name updated event published for user: {}", profile.getId());
        } catch (Exception e) {
            log.error("Failed to publish name updated event for user: {}", profile.getId(), e);
        }
    }

    @Transactional
    public ProfileImageUploadResponse uploadProfileImage(UUID userId, MultipartFile file) {
        log.info("Uploading profile image for user: {}", userId);

        var profile = profileRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Delete old image if exists
        if (profile.getProfileImageUrl() != null) {
            log.debug("Deleting old profile image for user: {}", userId);
            fileStorageService.deleteProfileImage(profile.getProfileImageUrl());
        }

        // Upload new image - returns GCS path (e.g., "profiles/userId.jpg")
        String gcsPath = fileStorageService.uploadProfileImage(file, userId);

        // Store GCS path (not signed URL) in database
        profile.setProfileImageUrl(gcsPath);
        profileRepository.save(profile);

        log.info("Profile image uploaded successfully for user: {}. GCS path: {}", userId, gcsPath);

        // Generate signed URL for immediate response (valid for 60 minutes)
        String signedUrl = fileStorageService.generateSignedUrl(gcsPath);

        return ProfileImageUploadResponse.success(
            signedUrl,
            gcsProperties.signedUrlDurationMinutes().longValue()
        );
    }
}
