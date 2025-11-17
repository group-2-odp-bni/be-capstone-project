package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.KafkaTopicProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.event.DomainEventFactory;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.model.enums.TokenScope;
import com.bni.orange.authentication.model.enums.UserStatus;
import com.bni.orange.authentication.model.request.AuthRequest;
import com.bni.orange.authentication.model.request.OtpVerifyRequest;
import com.bni.orange.authentication.model.response.ApiResponse;
import com.bni.orange.authentication.model.response.OtpResponse;
import com.bni.orange.authentication.model.response.StateTokenResponse;
import com.bni.orange.authentication.model.response.TokenResponse;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.service.captcha.CaptchaService;
import com.bni.orange.authentication.util.ResponseBuilder;
import com.bni.orange.authentication.validator.PinValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

import static com.bni.orange.authentication.util.PhoneNumberUtil.normalizePhoneNumber;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthFlowService {

    private static final long STATE_TOKEN_EXPIRES_IN_SECONDS = 300L;

    private final UserRepository userRepository;
    private final OtpService otpService;
    private final TokenService tokenService;
    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;
    private final PinValidator pinValidator;
    private final EventPublisher eventPublisher;
    private final KafkaTopicProperties topicProperties;
    private final CaptchaService captchaService;

    private final Executor virtualThreadTaskExecutor;

    @Transactional
    public ApiResponse<OtpResponse> requestLoginOtp(AuthRequest request, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());

        if (loginAttemptService.isLocked(normalizedPhoneNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        var captchaValidation = createCaptchaValidationFuture(request.captchaToken(), "login");
        var cooldownCheck = createCooldownCheckFuture(normalizedPhoneNumber, false);
        var userFuture = CompletableFuture.supplyAsync(() ->
            userRepository
                .findByPhoneNumber(normalizedPhoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)), virtualThreadTaskExecutor);

        joinAllAndHandleExceptions(captchaValidation, cooldownCheck, userFuture);

        var user = userFuture.join();
        return processAndSendOtp(user, servletRequest, false);
    }

    @Transactional
    public ApiResponse<OtpResponse> requestPinResetOtp(AuthRequest request, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());

        var captchaValidation = createCaptchaValidationFuture(request.captchaToken(), "login");
        var cooldownCheck = createCooldownCheckFuture(normalizedPhoneNumber, true); // isReset = true
        var userFuture = CompletableFuture.supplyAsync(() ->
            userRepository
                .findByPhoneNumber(normalizedPhoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)), virtualThreadTaskExecutor);

        joinAllAndHandleExceptions(captchaValidation, cooldownCheck, userFuture);

        var user = userFuture.join();
        return processAndSendOtp(user, servletRequest, true);
    }

    @Transactional
    public ApiResponse<OtpResponse> requestRegistrationOtp(AuthRequest request, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());

        if (loginAttemptService.isLocked(normalizedPhoneNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        var captchaValidation = createCaptchaValidationFuture(request.captchaToken(), "register");
        var userCheck = CompletableFuture.runAsync(
            () -> userRepository
                .findByPhoneNumber(normalizedPhoneNumber)
                .ifPresent(u -> {
                    throw new BusinessException(ErrorCode.USER_ALREADY_EXISTS);
                }),
            virtualThreadTaskExecutor);
        var cooldownCheck = createCooldownCheckFuture(normalizedPhoneNumber, false);

        joinAllAndHandleExceptions(captchaValidation, userCheck, cooldownCheck);

        var newUser = User.builder()
            .userPins("")
            .name("New User")
            .phoneNumber(normalizedPhoneNumber)
            .status(UserStatus.PENDING_VERIFICATION)
            .build();
        var user = userRepository.save(newUser);

        return processAndSendOtp(user, servletRequest, false);
    }

    private void validateCaptcha(String captchaToken, String expectedAction) {
        var captchaValid = captchaService.validateToken(captchaToken, expectedAction).block();
        if (!Boolean.TRUE.equals(captchaValid)) {
            throw new BusinessException(ErrorCode.INVALID_CAPTCHA);
        }
    }

    private ApiResponse<OtpResponse> processAndSendOtp(User user, HttpServletRequest servletRequest, boolean isPinReset) {
        var otp = otpService.generateAndStoreOtp(user.getPhoneNumber());

        var otpEvent = DomainEventFactory.createOtpNotificationEvent(
            user.getPhoneNumber(),
            otp,
            user.getId().toString()
        );

        var topicName = topicProperties.definitions().get("otp-notification").name();
        eventPublisher.publish(topicName, user.getPhoneNumber(), otpEvent);

        if (log.isDebugEnabled()) {
            log.debug("DEV MODE - OTP for {}: {}", user.getPhoneNumber(), otp);
        }

        if (isPinReset) {
            otpService.setCooldownReset(user.getPhoneNumber());
        } else {
            otpService.setCooldown(user.getPhoneNumber());
        }

        var data = OtpResponse.builder()
            .channel("whatsapp")
            .expiresIn(300)
            .build();

        return ResponseBuilder.success("OTP sent successfully", data, servletRequest);
    }

    @Transactional
    public ApiResponse<StateTokenResponse> verifyOtp(OtpVerifyRequest request, String purpose, HttpServletRequest servletRequest) {
        return internalVerifyOtp(request, purpose, servletRequest);
    }

    @Transactional
    public ApiResponse<StateTokenResponse> verifyOtp(OtpVerifyRequest request, HttpServletRequest servletRequest) {
        return internalVerifyOtp(request, null, servletRequest);
    }

    private ApiResponse<StateTokenResponse> internalVerifyOtp(OtpVerifyRequest request, String purpose, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());
        if (!otpService.isOtpValid(normalizedPhoneNumber, request.otp())) {
            throw new BusinessException(ErrorCode.INVALID_OTP);
        }

        var user = userRepository.findByPhoneNumber(normalizedPhoneNumber)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        var scope = determineTokenScope(user, purpose);
        var stateToken = tokenService.generateStateToken(user, scope);

        var data = StateTokenResponse.builder()
            .stateToken(stateToken)
            .expiresIn(STATE_TOKEN_EXPIRES_IN_SECONDS)
            .build();

        return ResponseBuilder.success("OTP verified successfully", data, servletRequest);
    }

    private String determineTokenScope(User user, String purpose) {
        if ("RESET".equals(purpose)) {
            return TokenScope.PIN_RESET.getValue();
        }

        return user.getStatus().equals(UserStatus.PENDING_VERIFICATION) ? TokenScope.PIN_SETUP.getValue() : TokenScope.PIN_LOGIN.getValue();
    }

    private CompletableFuture<Void> createCaptchaValidationFuture(String captchaToken, String expectedAction) {
        return CompletableFuture.runAsync(() ->
            validateCaptcha(captchaToken, expectedAction), virtualThreadTaskExecutor);
    }

    private CompletableFuture<Void> createCooldownCheckFuture(String phoneNumber, boolean isReset) {
        return CompletableFuture.runAsync(() -> {
            if (isReset) {
                if (otpService.isCooldownReset(phoneNumber)) {
                    throw new BusinessException(ErrorCode.OTP_COOLDOWN_RESET);
                }
            } else {
                if (otpService.isCooldown(phoneNumber)) {
                    throw new BusinessException(ErrorCode.OTP_COOLDOWN);
                }
            }
        }, virtualThreadTaskExecutor);
    }

    private void joinAllAndHandleExceptions(CompletableFuture<?>... futures) {
        try {
            CompletableFuture.allOf(futures).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof BusinessException be) {
                throw be;
            }
            log.error("Error during parallel validation", e);
            throw new BusinessException(ErrorCode.GENERAL_ERROR, e.getMessage());
        }
    }

    @Transactional
    public ApiResponse<TokenResponse> authenticateWithPin(UUID userId, String pin, String scope, String jti, HttpServletRequest request) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (loginAttemptService.isLocked(user.getPhoneNumber())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        loginAttemptService.applyProgressiveDelay(user.getPhoneNumber());

        if (TokenScope.PIN_SETUP.getValue().equals(scope) && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            pinValidator.validate(pin);

            user.setUserPins(passwordEncoder.encode(pin));
            user.setStatus(UserStatus.ACTIVE);
            user.setPhoneVerified(true);
            userRepository.save(user);

            var userRegisteredEvent = DomainEventFactory.createUserRegisteredEvent(user);
            var userRegisteredTopic = topicProperties.definitions().get("user-registered").name();
            eventPublisher.publish(userRegisteredTopic, user.getId().toString(), userRegisteredEvent);
        } else if (TokenScope.PIN_LOGIN.getValue().equals(scope) && user.getStatus() == UserStatus.ACTIVE) {
            if (!passwordEncoder.matches(pin, user.getUserPins())) {
                loginAttemptService.loginFailed(user.getPhoneNumber());
                var attemptsLeft = loginAttemptService.getAttemptsLeft(user.getPhoneNumber());
                throw new BusinessException(ErrorCode.INVALID_PIN, String.format("Invalid PIN. You have %d attempts left.", (Integer) attemptsLeft));
            }
        } else {
            throw new BusinessException(ErrorCode.INVALID_TOKEN_SCOPE);
        }

        loginAttemptService.loginSucceeded(user.getPhoneNumber());

        tokenService.consumeStateToken(jti);

        var ipAddress = Optional.ofNullable(request.getHeader("X-FORWARDED-FOR")).orElse(request.getRemoteAddr());
        var userAgent = request.getHeader("User-Agent");

        var tokenResponse = tokenService.generateTokens(user, ipAddress, userAgent);

        return ResponseBuilder.success("Authentication successful", tokenResponse, request);
    }
}