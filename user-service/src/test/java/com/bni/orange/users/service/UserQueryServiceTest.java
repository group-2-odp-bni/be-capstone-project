package com.bni.orange.users.service;

import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.model.entity.UserProfile;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    @Test
    void getCurrentUserProfile_whenUserExists_shouldReturnProfile() {
        var userId = UUID.randomUUID();
        UserProfile userProfile = new UserProfile();
        userProfile.setId(userId);
        userProfile.setName("John Doe");
        userProfile.setEmail("john.doe@example.com");
        userProfile.setPhoneNumber("1234567890");
        userProfile.setProfileImageUrl("http://example.com/profile.jpg");
        userProfile.setEmailVerifiedAt(OffsetDateTime.now());
        userProfile.setPhoneVerifiedAt(null);
        userProfile.setBio("A short bio");

        when(userProfileRepository.findById(userId)).thenReturn(Optional.of(userProfile));

        UserProfileResponse response = userQueryService.getCurrentUserProfile(userId);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john.doe@example.com", response.getEmail());
        assertEquals("1234567890", response.getPhoneNumber());
        assertEquals("http://example.com/profile.jpg", response.getProfileImageUrl());
        assertTrue(response.getEmailVerified());
        assertFalse(response.getPhoneVerified());
        assertEquals("A short bio", response.getBio());
        assertNotNull(response.getEmailVerifiedAt());

        verify(userProfileRepository, times(1)).findById(userId);
    }

    @Test
    void getCurrentUserProfile_whenUserNotFound_shouldThrowBusinessException() {
        var userId = UUID.randomUUID();
        when(userProfileRepository.findById(userId)).thenReturn(Optional.empty());

        var exception = assertThrows(BusinessException.class, () -> userQueryService.getCurrentUserProfile(userId));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userProfileRepository, times(1)).findById(userId);
    }
}
