package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.KafkaTopicProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.event.DomainEventFactory;
import com.bni.orange.authentication.proto.UserRegisteredEvent;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.model.enums.TokenScope;
import com.bni.orange.authentication.model.enums.UserStatus;
import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.response.TokenResponse;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.service.captcha.CaptchaService;
import com.bni.orange.authentication.service.redis.PendingRegistrationService;
import com.bni.orange.authentication.validator.PinValidator;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Mono;

import java.time.Instant;
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
    @Mock
    private PendingRegistrationService pendingRegistrationService;

    private User existingUser;
    private final String phoneNumber = "081234567890";
    private final String normalizedPhone = "+6281234567890";
    private final String otp = "123456";
    private final String pin = "112233";
    private final UUID userId = UUID.randomUUID();
    private final String captchaToken = "test-captcha-token";
    private final String dummyStateToken = "dummy-state-token";
    private Jwt mockJwt;

    @BeforeEach
    void setUp() {
        existingUser = User.builder()
                .id(userId)
                .phoneNumber(normalizedPhone)
                .status(UserStatus.ACTIVE)
                .userPins("encodedPin")
                .build();

        when(servletRequest.getRequestURI()).thenReturn("/api/v1/auth/test");
        when(captchaService.validateToken(anyString(), anyString())).thenReturn(Mono.just(true));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(virtualThreadTaskExecutor).execute(any(Runnable.class));

        var otpTopicConfig = new KafkaTopicProperties.TopicConfig("notification.otp.whatsapp", 3, 1, false);
        var regTopicConfig = new KafkaTopicProperties.TopicConfig("user.registered", 3, 1, true);
        when(topicProperties.definitions()).thenReturn(Map.of("otp-notification", otpTopicConfig, "user-registered", regTopicConfig));
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

            var response = authFlowService.requestLoginOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP sent successfully", response.getMessage());
            verify(eventPublisher).publish(eq("notification.otp.whatsapp"), eq(normalizedPhone), any());
            verify(otpService).setCooldown(normalizedPhone);
            verify(pendingRegistrationService, never()).exists(anyString());
        }

        @Test
        @DisplayName("Should send OTP for user with pending registration (login endpoint)")
        void requestLoginOtp_forPendingRegistrationUser_shouldSendOtpForPinSetup() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(false);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());
            when(pendingRegistrationService.exists(normalizedPhone)).thenReturn(true);
            when(otpService.generateAndStoreOtp(normalizedPhone)).thenReturn(otp);

            var response = authFlowService.requestLoginOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP sent successfully", response.getMessage());
            verify(pendingRegistrationService).exists(normalizedPhone);
        }

        @Test
        @DisplayName("Should throw UserNotFound if user not in DB and no pending registration")
        void requestLoginOtp_forNewUser_shouldThrowUserNotFound() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(false);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());
            when(pendingRegistrationService.exists(normalizedPhone)).thenReturn(false);

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestLoginOtp(request, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
            verify(pendingRegistrationService).exists(normalizedPhone);
        }
    }

    @Nested
    @DisplayName("Request Registration OTP Flow")
    class RequestRegistrationOtpFlow {

        @Test
        @DisplayName("Should save pending registration and send OTP for new user")
        void requestRegistrationOtp_forNewUser_shouldSavePendingRegistrationAndSendOtp() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(otpService.isCooldown(normalizedPhone)).thenReturn(false);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());
            doNothing().when(pendingRegistrationService).save(normalizedPhone);
            when(otpService.generateAndStoreOtp(normalizedPhone)).thenReturn(otp);

            var response = authFlowService.requestRegistrationOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("OTP sent successfully", response.getMessage());
            verify(userRepository, never()).save(any(User.class));
            verify(pendingRegistrationService).save(normalizedPhone);
        }

        @Test
        @DisplayName("Should throw UserAlreadyExists for existing user")
        void requestRegistrationOtp_forExistingUser_shouldThrowUserAlreadyExists() {
            var request = new AuthRequest(phoneNumber, captchaToken);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.of(existingUser));

            var exception = assertThrows(BusinessException.class, () -> authFlowService.requestRegistrationOtp(request, servletRequest));

            assertEquals(ErrorCode.USER_ALREADY_EXISTS, exception.getErrorCode());
            verify(pendingRegistrationService, never()).save(anyString());
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
            when(tokenService.generateStateToken(any(User.class), eq(TokenScope.PIN_LOGIN.getValue()))).thenReturn("state-token-login");

            var response = authFlowService.verifyOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("state-token-login", response.getData().stateToken());
            verify(pendingRegistrationService, never()).exists(anyString());
        }

        @Test
        @DisplayName("Should return state token for PIN_SETUP for user with pending registration")
        void verifyOtp_forPendingRegistrationUser_shouldReturnStateTokenForPinSetup() {
            var request = new OtpVerifyRequest(phoneNumber, otp);
            when(otpService.isOtpValid(normalizedPhone, otp)).thenReturn(true);
            when(userRepository.findByPhoneNumber(normalizedPhone)).thenReturn(Optional.empty());
            when(pendingRegistrationService.exists(normalizedPhone)).thenReturn(true);
            when(tokenService.generateStateToken(any(User.class), eq(TokenScope.PIN_SETUP.getValue()))).thenReturn("state-token-setup");

            var response = authFlowService.verifyOtp(request, servletRequest);

            assertNotNull(response);
            assertEquals("state-token-setup", response.getData().stateToken());
            verify(pendingRegistrationService).exists(normalizedPhone);
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
                        try (MockedStatic<DomainEventFactory> mockedFactory = Mockito.mockStatic(DomainEventFactory.class)) {
                            var userRegisteredEvent = UserRegisteredEvent.newBuilder().build();
                            mockedFactory.when(() -> DomainEventFactory.createUserRegisteredEvent(any(User.class)))
                                    .thenReturn(userRegisteredEvent);
                mockJwt = Jwt.withTokenValue(dummyStateToken)
                    .header("alg", "none")
                    .claim("scope", TokenScope.PIN_SETUP.getValue())
                    .claim("sub", normalizedPhone)
                    .claim("jti", jti)
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(300))
                    .build();
                when(tokenService.decodeStateToken(dummyStateToken)).thenReturn(mockJwt);

                when(loginAttemptService.isLocked(normalizedPhone)).thenReturn(false);
                when(pendingRegistrationService.exists(normalizedPhone)).thenReturn(true);
                doNothing().when(pendingRegistrationService).delete(normalizedPhone);
                doNothing().when(pinValidator).validate(pin);
                when(passwordEncoder.encode(pin)).thenReturn("encodedNewPin");

                User savedUser = User.builder()
                    .id(UUID.randomUUID())
                    .phoneNumber(normalizedPhone)
                    .status(UserStatus.ACTIVE)
                    .userPins("encodedNewPin")
                    .name("New User")
                    .build();
                when(userRepository.save(any(User.class))).thenReturn(savedUser);

                var tokenResponse = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
                when(tokenService.generateTokens(any(User.class), eq(ipAddress), eq(userAgent))).thenReturn(tokenResponse);

                var response = authFlowService.authenticateWithPin(dummyStateToken, pin, servletRequest);

                assertNotNull(response);
                assertEquals("Authentication successful", response.getMessage());

                verify(eventPublisher).publish(eq("user.registered"), eq(savedUser.getId().toString()), any());
                verify(loginAttemptService).loginSucceeded(normalizedPhone);
            }
        }

        @Test
        @DisplayName("Should throw exception if pending registration state is expired for PIN_SETUP")
        void authenticateWithPin_forPinSetup_whenPendingRegistrationExpired_shouldThrowException() {
            mockJwt = Jwt.withTokenValue(dummyStateToken)
                .header("alg", "none")
                .claim("scope", TokenScope.PIN_SETUP.getValue())
                .claim("sub", normalizedPhone)
                .claim("jti", jti)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
            when(tokenService.decodeStateToken(dummyStateToken)).thenReturn(mockJwt);

            when(loginAttemptService.isLocked(normalizedPhone)).thenReturn(false);
            when(pendingRegistrationService.exists(normalizedPhone)).thenReturn(false);

            var exception = assertThrows(BusinessException.class, () ->
                authFlowService.authenticateWithPin(dummyStateToken, pin, servletRequest));

            assertEquals(ErrorCode.REGISTRATION_STATE_EXPIRED, exception.getErrorCode());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("Should return tokens for valid PIN with PIN_LOGIN scope")
        void authenticateWithPin_forPinLogin_withValidPin_shouldSucceed() {
            mockJwt = Jwt.withTokenValue(dummyStateToken)
                .header("alg", "none")
                .claim("scope", TokenScope.PIN_LOGIN.getValue())
                .claim("sub", userId.toString())
                .claim("jti", jti)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
            when(tokenService.decodeStateToken(dummyStateToken)).thenReturn(mockJwt);

            when(loginAttemptService.isLocked(existingUser.getPhoneNumber())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches(pin, "encodedPin")).thenReturn(true);

            var tokenResponse = TokenResponse.builder().accessToken("access").refreshToken("refresh").build();
            when(tokenService.generateTokens(existingUser, ipAddress, userAgent)).thenReturn(tokenResponse);

            var response = authFlowService.authenticateWithPin(dummyStateToken, pin, servletRequest);

            assertNotNull(response);
            assertEquals("Authentication successful", response.getMessage());
            verify(loginAttemptService).loginSucceeded(existingUser.getPhoneNumber());
        }

        @Test
        @DisplayName("Should throw exception for invalid PIN with PIN_LOGIN scope")
        void authenticateWithPin_forPinLogin_withInvalidPin_shouldFail() {
            mockJwt = Jwt.withTokenValue(dummyStateToken)
                .header("alg", "none")
                .claim("scope", TokenScope.PIN_LOGIN.getValue())
                .claim("sub", userId.toString())
                .claim("jti", jti)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
            when(tokenService.decodeStateToken(dummyStateToken)).thenReturn(mockJwt);

            when(loginAttemptService.isLocked(existingUser.getPhoneNumber())).thenReturn(false);
            when(userRepository.findById(userId)).thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("wrong-pin", "encodedPin")).thenReturn(false);
            when(loginAttemptService.getAttemptsLeft(existingUser.getPhoneNumber())).thenReturn(4);

            var exception = assertThrows(BusinessException.class, () ->
                authFlowService.authenticateWithPin(dummyStateToken, "wrong-pin", servletRequest));

            assertEquals(ErrorCode.INVALID_PIN, exception.getErrorCode());
            assertTrue(exception.getMessage().contains("4 attempts left"));
            verify(loginAttemptService).loginFailed(existingUser.getPhoneNumber());
        }
    }
}
