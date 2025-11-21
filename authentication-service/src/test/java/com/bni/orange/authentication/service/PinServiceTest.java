package com.bni.orange.authentication.service;

import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.model.request.PinChangeRequest;
import com.bni.orange.authentication.model.request.PinResetConfirmRequest;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.validator.PinValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PinServiceTest {

    @InjectMocks
    private PinService pinService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PinValidator pinValidator;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private User user;

    private final UUID userId = UUID.randomUUID();
    private final String userPinHash = "encodedCurrentPin";

    @BeforeEach
    void setUp() {
        when(servletRequest.getRequestURI()).thenReturn("/api/v1/pin/test");
        when(user.getUserPins()).thenReturn(userPinHash);
    }

    @Nested
    @DisplayName("changePin Tests")
    class ChangePinTests {

        private final PinChangeRequest validChangeRequest = new PinChangeRequest("123456", "654321");

        @Test
        @DisplayName("Should change PIN and revoke tokens on valid request")
        void changePin_withValidData_shouldSucceed() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(validChangeRequest.currentPin(), userPinHash)).thenReturn(true);
            doNothing().when(pinValidator).validate(validChangeRequest.newPin());
            when(passwordEncoder.encode(validChangeRequest.newPin())).thenReturn("encodedNewPin");

            var response = pinService.changePin(userId, validChangeRequest, servletRequest);

            assertNotNull(response);
            assertEquals("PIN changed successfully", response.getMessage());
            verify(user).setUserPins("encodedNewPin");
            verify(userRepository).save(user);
            verify(refreshTokenRepository).revokeAllByUser(user);
        }

        @Test
        @DisplayName("Should throw USER_NOT_FOUND when user does not exist")
        void changePin_whenUserNotFound_shouldThrowException() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, 
                () -> pinService.changePin(userId, validChangeRequest, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw INVALID_CURRENT_PIN on incorrect current PIN")
        void changePin_withInvalidCurrentPin_shouldThrowException() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(validChangeRequest.currentPin(), userPinHash)).thenReturn(false);

            var exception = assertThrows(BusinessException.class, 
                () -> pinService.changePin(userId, validChangeRequest, servletRequest));

            assertEquals(ErrorCode.INVALID_CURRENT_PIN, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception when new PIN is invalid")
        void changePin_withInvalidNewPin_shouldThrowFromValidator() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(validChangeRequest.currentPin(), userPinHash)).thenReturn(true);
            doThrow(new BusinessException(ErrorCode.INVALID_PIN, "PIN is too weak."))
                .when(pinValidator).validate(validChangeRequest.newPin());

            var exception = assertThrows(BusinessException.class, 
                () -> pinService.changePin(userId, validChangeRequest, servletRequest));

            assertEquals(ErrorCode.INVALID_PIN, exception.getErrorCode());
            assertEquals("PIN is too weak.", exception.getMessage());
        }
    }

    @Nested
    @DisplayName("confirmPinReset Tests")
    class ConfirmPinResetTests {

        private final PinResetConfirmRequest validResetRequest = new PinResetConfirmRequest("987654");

        @Test
        @DisplayName("Should reset PIN on valid request")
        void confirmPinReset_withValidData_shouldSucceed() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            doNothing().when(pinValidator).validate(validResetRequest.newPin());
            when(passwordEncoder.encode(validResetRequest.newPin())).thenReturn("encodedResetPin");

            var response = pinService.confirmPinReset(userId, validResetRequest, servletRequest);

            assertNotNull(response);
            assertEquals("PIN reset successfully", response.getMessage());
            verify(user).setUserPins("encodedResetPin");
            verify(userRepository).save(user);
            verify(refreshTokenRepository, never()).revokeAllByUser(any());
        }

        @Test
        @DisplayName("Should throw USER_NOT_FOUND when user does not exist")
        void confirmPinReset_whenUserNotFound_shouldThrowException() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, 
                () -> pinService.confirmPinReset(userId, validResetRequest, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception when new PIN is invalid")
        void confirmPinReset_withInvalidNewPin_shouldThrowFromValidator() {
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            doThrow(new BusinessException(ErrorCode.INVALID_PIN, "PIN contains sequential numbers."))
                .when(pinValidator).validate(validResetRequest.newPin());

            var exception = assertThrows(BusinessException.class, 
                () -> pinService.confirmPinReset(userId, validResetRequest, servletRequest));

            assertEquals(ErrorCode.INVALID_PIN, exception.getErrorCode());
            assertEquals("PIN contains sequential numbers.", exception.getMessage());
        }
    }
}
