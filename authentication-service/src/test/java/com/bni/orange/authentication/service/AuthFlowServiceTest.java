package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.KafkaTopicProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.model.enums.TokenScope;
import com.bni.orange.authentication.model.enums.UserStatus;
import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.response.TokenResponse;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.service.captcha.CaptchaService;
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
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthFlowServiceTest {

    @InjectMocks
    private AuthFlowService authFlowService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private OtpService otpService;
    @Mock
    private TokenService tokenService;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PinValidator pinValidator;
    @Mock
    private EventPublisher eventPublisher;
    @Mock
    private KafkaTopicProperties topicProperties;
    @Mock
    private CaptchaService captchaService;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private Executor virtualThreadTaskExecutor;

    private User existingUser;
    private User newUser;
    private final String phoneNumber = "081234567890";
    private final String normalizedPhone = "+6281234567890";
    private final String otp = "123456";
    private final String pin = "112233";
    private final UUID userId = UUID.randomUUID();
    private final String captchaToken = "test-captcha-token";

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(userId)
                .phoneNumber(normalizedPhone)
                .status(UserStatus.ACTIVE)
                .userPins("encodedPin")
                .build();

        newUser = User.builder()
                .id(UUID.randomUUID())
                .phoneNumber(normalizedPhone)
                .status(UserStatus.PENDING_VERIFICATION)
                .userPins("")
                .name("New User")
                .build();

        when(servletRequest.getRequestURI()).thenReturn("/api/v1/auth/test");
        when(captchaService.validateToken(anyString(), anyString())).thenReturn(Mono.just(true));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(virtualThreadTaskExecutor).execute(any(Runnable.class));
    }

    @Nested
    @DisplayName("Request Login OTP Flow")
    class RequestLoginOtpFlow {

        @Test
        @DisplayName("Should send OTP for existing user")
        void requestLoginOtp_forExistingUser_shouldSendOtp() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(false);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(existingUser));
            when(otpService.generateAndStoreOtp(normalizedPhone)).thenReturn(otp);

            var topicConfig = new KafkaTopicProperties.TopicConfig("notification.otp.whatsapp", 3, 1, false);
            when(topicProperties.definitions()).thenReturn(Map.of("otp-notification", topicConfig));

            var response = authFlowService.requestLoginOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP sent successfully", response.getMessage());
            assertNotNull(response.getData());
            assertEquals("whatsapp", response.getData().channel());

            verify(captchaService).validateToken(captchaToken, "login");
            verify(eventPublisher).publish(eq("notification.otp.whatsapp"), eq(normalizedPhone), any());
            verify(otpService).setCooldown(normalizedPhone);
        }

        @Test
        @DisplayName("Should throw UserNotFound for new user")
        void requestLoginOtp_forNewUser_shouldThrowUserNotFound() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(false);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestLoginOtp(request, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
            verify(captchaService).validateToken(captchaToken, "login");
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should throw exception if on cooldown")
        void requestLoginOtp_whenOnCooldown_shouldThrowException() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(true);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(existingUser));


            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestLoginOtp(request, servletRequest));

            assertEquals(ErrorCode.OTP_COOLDOWN, exception.getErrorCode());
            verify(captchaService).validateToken(captchaToken, "login");
        }

        @Test
        @DisplayName("Should throw exception for invalid captcha")
        void requestLoginOtp_withInvalidCaptcha_shouldThrowException() {
            var request = new AuthRequest(phoneNumber, "invalid-token");
            when(captchaService.validateToken("invalid-token", "login")).thenReturn(Mono.just(false));

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestLoginOtp(request, servletRequest));

            assertEquals(ErrorCode.INVALID_CAPTCHA, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Request Registration OTP Flow")
    class RequestRegistrationOtpFlow {

        @Test
        @DisplayName("Should create new user and send OTP")
        void requestRegistrationOtp_forNewUser_shouldCreateUserAndSendOtp() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(false);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());
            when(userRepository.save(any(User.class))).thenReturn(newUser);
            when(otpService.generateAndStoreOtp(normalizedPhone)).thenReturn(otp);

            var topicConfig = new KafkaTopicProperties.TopicConfig("notification.otp.whatsapp", 3, 1, false);
            when(topicProperties.definitions()).thenReturn(Map.of("otp-notification", topicConfig));

            var response = authFlowService.requestRegistrationOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP sent successfully", response.getMessage());
            verify(captchaService).validateToken(captchaToken, "register");
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(eq("notification.otp.whatsapp"), eq(normalizedPhone), any());
        }

        @Test
        @DisplayName("Should throw UserAlreadyExists for existing user")
        void requestRegistrationOtp_forExistingUser_shouldThrowUserAlreadyExists() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(existingUser));

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestRegistrationOtp(request, servletRequest));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
            verify(captchaService).validateToken(captchaToken, "register");
        }

        @Test
        @DisplayName("Should throw exception if on cooldown")
        void requestRegistrationOtp_whenOnCooldown_shouldThrowException() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());
            when(otpService.isCooldown(normalizedPhone)).thenReturn(true);

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestRegistrationOtp(request, servletRequest));

            assertEquals(ErrorCode.OTP_COOLDOWN, exception.getErrorCode());
            verify(captchaService).validateToken(captchaToken, "register");
        }

        @Test
        @DisplayName("Should throw exception for invalid captcha")
        void requestRegistrationOtp_withInvalidCaptcha_shouldThrowException() {
            var request = new AuthRequest(phoneNumber, "invalid-token");
            when(captchaService.validateToken("invalid-token", "register")).thenReturn(Mono.just(false));

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestRegistrationOtp(request, servletRequest));

            assertEquals(ErrorCode.INVALID_CAPTCHA, exception.getErrorCode());
        }
    }


    @Nested
    @DisplayName("Verify OTP Flow")
    class VerifyOtpFlow {

        @Test
        @DisplayName("Should return state token for PIN_LOGIN for existing user")
        void verifyOtp_forExistingUser_shouldReturnStateTokenForLogin() {
            var request = new OtpVerifyRequest(phoneNumber, otp);
            when(otpService.isOtpValid(normalizedPhone, otp)).thenReturn(true);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(existingUser));
            when(tokenService.generateStateToken(existingUser, TokenScope.PIN_LOGIN.getValue())).thenReturn("state-token-login");

            var response = authFlowService.verifyOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP verified successfully", response.getMessage());
            assertEquals("state-token-login", response.getData().stateToken());
            assertEquals(300L, response.getData().expiresIn());
        }

        @Test
        @DisplayName("Should return state token for PIN_SETUP for new user")
        void verifyOtp_forNewUser_shouldReturnStateTokenForPinSetup() {
            var request = new OtpVerifyRequest(phoneNumber, otp);
            when(otpService.isOtpValid(normalizedPhone, otp)).thenReturn(true);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(newUser));
            when(tokenService.generateStateToken(newUser, TokenScope.PIN_SETUP.getValue())).thenReturn("state-token-setup");

            var response = authFlowService.verifyOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP verified successfully", response.getMessage());
            assertEquals("state-token-setup", response.getData().stateToken());
        }

        @Test
        @DisplayName("Should return state token for PIN_RESET when purpose is RESET")
        void verifyOtp_forPinReset_shouldReturnStateTokenForPinReset() {
            var request = new OtpVerifyRequest(phoneNumber, otp);
            when(otpService.isOtpValid(normalizedPhone, otp)).thenReturn(true);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(existingUser));
            when(tokenService.generateStateToken(existingUser, TokenScope.PIN_RESET.getValue())).thenReturn("state-token-reset");

            var response = authFlowService.verifyOtp(request, "RESET", servletRequest);

            assertNotNull(response);
            assertEquals("OTP verified successfully", response.getMessage());
            assertEquals("state-token-reset", response.getData().stateToken());
        }

        @Test
        @DisplayName("Should throw exception for invalid OTP")
        void verifyOtp_withInvalidOtp_shouldThrowException() {
            var request = new OtpVerifyRequest(phoneNumber, "wrong-otp");
            when(otpService.isOtpValid(normalizedPhone, "wrong-otp")).thenReturn(false);

            var exception = assertThrows(BusinessException.class, () -> authFlowService.verifyOtp(request, servletRequest));

            assertEquals(ErrorCode.INVALID_OTP, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception if user not found")
        void verifyOtp_whenUserNotFound_shouldThrowException() {
            var request = new OtpVerifyRequest(phoneNumber, otp);
            when(otpService.isOtpValid(normalizedPhone, otp)).thenReturn(true);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, () -> authFlowService.verifyOtp(request, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("Authenticate With PIN Flow")
    class AuthenticateWithPinFlow {

        private final String jti = UUID.randomUUID().toString();
        private final String ipAddress = "127.0.0.1";
        private final String userAgent = "Test-Agent";

        @BeforeEach
        void authPinSetup() {
            when(servletRequest.getHeader("X-FORWARDED-FOR")).thenReturn(null);
            when(servletRequest.getRemoteAddr()).thenReturn(ipAddress);
            when(servletRequest.getHeader("User-Agent")).thenReturn(userAgent);
        }

        @Test
        @DisplayName("Should set PIN, activate user, and return tokens for PIN_SETUP scope")
        void authenticateWithPin_forPinSetup_shouldSucceed() {
            when(loginAttemptService.isLocked(newUser.getId().toString())).thenReturn(false);
            when(userRepository.findById(newUser.getId())).thenReturn(Optional.of(newUser));
            doNothing().when(pinValidator).validate(pin);
            when(passwordEncoder.encode(pin)).thenReturn("encodedNewPin");

            var topicConfig = new KafkaTopicProperties.TopicConfig("user.registered", 3, 1, true);
            when(topicProperties.definitions()).thenReturn(Map.of("user-registered", topicConfig));

            var tokenResponse = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
            when(tokenService.generateTokens(any(User.class), eq(ipAddress), eq(userAgent))).thenReturn(tokenResponse);

            var response = authFlowService.authenticateWithPin(newUser.getId(), pin, TokenScope.PIN_SETUP.getValue(), jti, servletRequest);

            assertNotNull(response);
            assertEquals("Authentication successful", response.getMessage());
            assertEquals("access", response.getData().accessToken());

            verify(tokenService).consumeStateToken(jti);
            verify(loginAttemptService).applyProgressiveDelay(newUser.getId().toString());
            verify(userRepository).save(any(User.class));
            verify(eventPublisher).publish(eq("user.registered"), eq(newUser.getId().toString()), any());
            verify(loginAttemptService).loginSucceeded(newUser.getId().toString());
        }

        @Test
        @DisplayName("Should return tokens for valid PIN with PIN_LOGIN scope")
        void authenticateWithPin_forPinLogin_withValidPin_shouldSucceed() {
            when(loginAttemptService.isLocked(userId.toString())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(pin, "encodedPin")).thenReturn(true);

            var tokenResponse = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
            when(tokenService.generateTokens(existingUser, ipAddress, userAgent)).thenReturn(tokenResponse);

            var response = authFlowService.authenticateWithPin(userId, pin, TokenScope.PIN_LOGIN.getValue(), jti, servletRequest);

            assertNotNull(response);
            assertEquals("Authentication successful", response.getMessage());

            verify(tokenService).consumeStateToken(jti);
            verify(loginAttemptService).loginSucceeded(userId.toString());
            verify(loginAttemptService, never()).loginFailed(anyString());
        }

        @Test
        @DisplayName("Should throw exception for invalid PIN with PIN_LOGIN scope")
        void authenticateWithPin_forPinLogin_withInvalidPin_shouldFail() {
            when(loginAttemptService.isLocked(userId.toString())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrong-pin", "encodedPin")).thenReturn(false);
            when(loginAttemptService.getAttemptsLeft(userId.toString())).thenReturn(4);

            var exception = assertThrows(BusinessException.class, () ->
                authFlowService.authenticateWithPin(userId, "wrong-pin", TokenScope.PIN_LOGIN.getValue(), jti, servletRequest));

            assertEquals(ErrorCode.INVALID_PIN, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("4 attempts left"));
            verify(loginAttemptService).loginFailed(userId.toString());
            verify(loginAttemptService, never()).loginSucceeded(anyString());
        }

        @Test
        @DisplayName("Should throw exception if account is locked")
        void authenticateWithPin_whenAccountIsLocked_shouldThrowException() {
            when(loginAttemptService.isLocked(userId.toString())).thenReturn(true);

            var exception = assertThrows(BusinessException.class, () ->
                authFlowService.authenticateWithPin(userId, pin, TokenScope.PIN_LOGIN.getValue(), jti, servletRequest));

            assertEquals(ErrorCode.ACCOUNT_LOCKED, exception.getErrorCode());
            verify(userRepository, never()).findById(any());
        }

        @Test
        @DisplayName("Should throw exception for invalid token scope")
        void authenticateWithPin_withInvalidScope_shouldThrowException() {
            when(loginAttemptService.isLocked(userId.toString())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));

            var exception = assertThrows(BusinessException.class, () ->
                authFlowService.authenticateWithPin(userId, pin, "INVALID_SCOPE", jti, servletRequest));

            assertEquals(ErrorCode.INVALID_TOKEN_SCOPE, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw exception if user not found")
        void authenticateWithPin_whenUserNotFound_shouldThrowException() {
            when(loginAttemptService.isLocked(userId.toString())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, () ->
                authFlowService.authenticateWithPin(userId, pin, TokenScope.PIN_LOGIN.getValue(), jti, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }
}