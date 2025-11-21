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
import com.bni.orange.authentication.service.redis.PendingRegistrationService;
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
    private final PendingRegistrationService pendingRegistrationService;

    private final Executor virtualThreadTaskExecutor;

    @Transactional
    public ApiResponse<OtpResponse> requestLoginOtp(AuthRequest request, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());

        if (loginAttemptService.isLocked(normalizedPhoneNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        var captchaValidation = createCaptchaValidationFuture(request.captchaToken(), "login");
        var cooldownCheck = createCooldownCheckFuture(normalizedPhoneNumber, false);

        joinAllAndHandleExceptions(captchaValidation, cooldownCheck);

        var userOpt = userRepository.findByPhoneNumber(normalizedPhoneNumber);

        if (userOpt.isPresent()) {
            return processAndSendOtp(userOpt.get().getPhoneNumber(), userOpt.get().getId().toString(), servletRequest, false);
        } else {
            if (pendingRegistrationService.exists(normalizedPhoneNumber)) {
                log.info("User with phone number {} not found in DB, but pending registration exists. Proceeding with PIN setup flow.", normalizedPhoneNumber);
                return processAndSendOtp(normalizedPhoneNumber, null, servletRequest, false);
            } else {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }
    }

    @Transactional
    public ApiResponse<OtpResponse> requestPinResetOtp(AuthRequest request, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());

        var captchaValidation = createCaptchaValidationFuture(request.captchaToken(), "login");
        var cooldownCheck = createCooldownCheckFuture(normalizedPhoneNumber, true);
        var userFuture = CompletableFuture.supplyAsync(() ->
            userRepository
                .findByPhoneNumber(normalizedPhoneNumber)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)), virtualThreadTaskExecutor);

        joinAllAndHandleExceptions(captchaValidation, cooldownCheck, userFuture);

        var user = userFuture.join();
        return processAndSendOtp(user.getPhoneNumber(), user.getId().toString(), servletRequest, true);
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

        pendingRegistrationService.save(normalizedPhoneNumber);
        log.info("Saved pending registration for phone number: {}", normalizedPhoneNumber);

        return processAndSendOtp(normalizedPhoneNumber, null, servletRequest, false);
    }

    private void validateCaptcha(String captchaToken, String expectedAction) {
        var captchaValid = captchaService.validateToken(captchaToken, expectedAction).block();
        if (!Boolean.TRUE.equals(captchaValid)) {
            throw new BusinessException(ErrorCode.INVALID_CAPTCHA);
        }
    }

    private ApiResponse<OtpResponse> processAndSendOtp(String phoneNumber, String userId, HttpServletRequest servletRequest, boolean isPinReset) {
        var otp = otpService.generateAndStoreOtp(phoneNumber);

        var otpEvent = DomainEventFactory.createOtpNotificationEvent(phoneNumber, otp, userId);

        var topicName = topicProperties.definitions().get("otp-notification").name();
        eventPublisher.publish(topicName, phoneNumber, otpEvent);

        if (log.isDebugEnabled()) {
            log.debug("DEV MODE - OTP for {}: {}", phoneNumber, otp);
        }

        if (isPinReset) {
            otpService.setCooldownReset(phoneNumber);
        } else {
            otpService.setCooldown(phoneNumber);
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

        Optional<User> userOpt = userRepository.findByPhoneNumber(normalizedPhoneNumber);
        String scope = determineTokenScope(userOpt, purpose, normalizedPhoneNumber);

        User userForToken = userOpt.orElseGet(() -> User.builder().phoneNumber(normalizedPhoneNumber).build());
        var stateToken = tokenService.generateStateToken(userForToken, scope);

        var data = StateTokenResponse.builder()
            .stateToken(stateToken)
            .expiresIn(STATE_TOKEN_EXPIRES_IN_SECONDS)
            .build();

        return ResponseBuilder.success("OTP verified successfully", data, servletRequest);
    }

    private String determineTokenScope(Optional<User> userOpt, String purpose, String phoneNumber) {
        if ("RESET".equals(purpose)) {
            return TokenScope.PIN_RESET.getValue();
        }

        return userOpt.map(user -> TokenScope.PIN_LOGIN.getValue())
            .orElseGet(() -> {
                if (pendingRegistrationService.exists(phoneNumber)) {
                    return TokenScope.PIN_SETUP.getValue();
                }
                log.error("Cannot determine token scope. User not found in DB and no pending registration in Redis for phone: {}", phoneNumber);
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            });
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
    public ApiResponse<TokenResponse> authenticateWithPin(String stateToken, String pin, HttpServletRequest request) {
        var jwt = tokenService.decodeStateToken(stateToken);
        var scope = jwt.getClaimAsString("scope");
        var subject = jwt.getSubject();
        var jti = jwt.getId();

        User user;
        String phoneNumber;

        if (TokenScope.PIN_SETUP.getValue().equals(scope)) {
            phoneNumber = subject;
            user = User.builder().phoneNumber(phoneNumber).build();
        } else if (TokenScope.PIN_LOGIN.getValue().equals(scope)) {
            UUID userId = UUID.fromString(subject);
            user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            phoneNumber = user.getPhoneNumber();
        } else {
            throw new BusinessException(ErrorCode.INVALID_TOKEN_SCOPE);
        }

        if (loginAttemptService.isLocked(phoneNumber)) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        loginAttemptService.applyProgressiveDelay(phoneNumber);

        if (TokenScope.PIN_SETUP.getValue().equals(scope)) {
            pinValidator.validate(pin);

            if (!pendingRegistrationService.exists(phoneNumber)) {
                log.warn("Attempt to set PIN without a pending registration state for phone: {}", phoneNumber);
                throw new BusinessException(ErrorCode.REGISTRATION_STATE_EXPIRED);
            }

            user.setUserPins(passwordEncoder.encode(pin));
            user.setStatus(UserStatus.ACTIVE);
            user.setPhoneVerified(true);
            user.setName("New User");
            User savedUser = userRepository.save(user);
            log.info("New user created and saved with ID: {}", savedUser.getId());

            var userRegisteredEvent = DomainEventFactory.createUserRegisteredEvent(savedUser);
            var topic = topicProperties.definitions().get("user-registered").name();
            eventPublisher.publish(topic, savedUser.getId().toString(), userRegisteredEvent);
            log.info("Published user registration event for user ID: {}", savedUser.getId());

            pendingRegistrationService.delete(phoneNumber);
            log.info("Cleared pending registration state for phone: {}", phoneNumber);

            user = savedUser;

        } else {
            if (!passwordEncoder.matches(pin, user.getUserPins())) {
                loginAttemptService.loginFailed(phoneNumber);
                var attemptsLeft = loginAttemptService.getAttemptsLeft(phoneNumber);
                throw new BusinessException(ErrorCode.INVALID_PIN, String.format("Invalid PIN. You have %d attempts left.", (Integer) attemptsLeft));
            }
        }

        loginAttemptService.loginSucceeded(phoneNumber);
        tokenService.consumeStateToken(jti);

        var ipAddress = Optional.ofNullable(request.getHeader("X-FORWARDED-FOR")).orElse(request.getRemoteAddr());
        var userAgent = request.getHeader("User-Agent");

        var tokenResponse = tokenService.generateTokens(user, ipAddress, userAgent);

        return ResponseBuilder.success("Authentication successful", tokenResponse, request);
    }
}