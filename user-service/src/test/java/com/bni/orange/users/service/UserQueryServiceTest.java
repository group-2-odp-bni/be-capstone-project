package com.bni.orange.users.service;

import com.bni.orange.users.error.BusinessException;
import com.bni.orange.users.error.ErrorCode;
import com.bni.orange.users.model.entity.UserProfileView;
import com.bni.orange.users.model.response.UserProfileResponse;
import com.bni.orange.users.repository.UserProfileViewRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
    private UserProfileViewRepository userProfileViewRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    @Test
    void getCurrentUserProfile_whenUserExists_shouldReturnProfile() {
        var userId = UUID.randomUUID();
        UserProfileView userProfileView = new UserProfileView();
        userProfileView.setId(userId);
        userProfileView.setName("John Doe");
        userProfileView.setEmail("john.doe@example.com");
        userProfileView.setPhoneNumber("1234567890");
        userProfileView.setProfileImageUrl("http://example.com/profile.jpg");
        userProfileView.setEmailVerified(true);
        userProfileView.setPhoneVerified(false);
        userProfileView.setStatus("ACTIVE");
        userProfileView.setLastLoginAt(LocalDateTime.now());

        when(userProfileViewRepository.findById(userId)).thenReturn(Optional.of(userProfileView));

        UserProfileResponse response = userQueryService.getCurrentUserProfile(userId);

        assertNotNull(response);
        assertEquals(userId, response.getId());
        assertEquals("John Doe", response.getName());
        assertEquals("john.doe@example.com", response.getEmail());
        assertEquals("1234567890", response.getPhoneNumber());
        assertEquals("http://example.com/profile.jpg", response.getProfileImageUrl());
        assertTrue(response.getEmailVerified());
        assertFalse(response.getPhoneVerified());
        assertEquals("ACTIVE", response.getStatus());
        assertNotNull(response.getLastLoginAt());

        verify(userProfileViewRepository, times(1)).findById(userId);
    }

    @Test
    void getCurrentUserProfile_whenUserNotFound_shouldThrowBusinessException() {
        var userId = UUID.randomUUID();
        when(userProfileViewRepository.findById(userId)).thenReturn(Optional.empty());

        var exception = assertThrows(BusinessException.class, () -> userQueryService.getCurrentUserProfile(userId));

        assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        verify(userProfileViewRepository, times(1)).findById(userId);
    }
}
