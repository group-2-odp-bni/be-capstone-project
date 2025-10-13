package com.bni.orange.authentication.service;

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

    @Transactional
    public ApiResponse<OtpResponse> requestOtp(AuthRequest request, HttpServletRequest servletRequest) {
        var normalizedPhoneNumber = normalizePhoneNumber(request.phoneNumber());
        if (otpService.isCooldown(normalizedPhoneNumber)) {
            throw new BusinessException(ErrorCode.OTP_COOLDOWN);
        }

        var user = userRepository.findByPhoneNumber(normalizedPhoneNumber)
            .orElseGet(() -> {
                var newUser = User.builder()
                    .userPins("")
                    .name("New User")
                    .phoneNumber(normalizedPhoneNumber)
                    .status(UserStatus.PENDING_VERIFICATION)
                    .build();
                return userRepository.save(newUser);
            });

        var otp = otpService.generateAndStoreOtp(user.getPhoneNumber());

        var otpEvent = DomainEventFactory.createOtpNotificationEvent(
            user.getPhoneNumber(),
            otp,
            user.getId().toString()
        );

        eventPublisher.publish("otp-notification", user.getPhoneNumber(), otpEvent);

        if (log.isDebugEnabled()) {
            log.debug("DEV MODE - OTP for {}: {}", user.getPhoneNumber(), otp);
        }

        otpService.setCooldown(user.getPhoneNumber());

        var data = OtpResponse.builder()
            .channel("whatsapp")
            .expiresIn(300)
            .build();

        return ResponseBuilder.success("OTP sent successfully", data, servletRequest);
    }

    @Transactional
    public ApiResponse<StateTokenResponse> verifyOtp(OtpVerifyRequest request, String purpose, HttpServletRequest servletRequest) {
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

    @Transactional
    public ApiResponse<StateTokenResponse> verifyOtp(OtpVerifyRequest request, HttpServletRequest servletRequest) {
        return verifyOtp(request, null, servletRequest);
    }

    private String determineTokenScope(User user, String purpose) {
        if ("RESET".equals(purpose)) {
            return TokenScope.PIN_RESET.getValue();
        }

        return user.getStatus().equals(UserStatus.PENDING_VERIFICATION) ? TokenScope.PIN_SETUP.getValue() : TokenScope.PIN_LOGIN.getValue();
    }

    @Transactional
    public ApiResponse<TokenResponse> authenticateWithPin(UUID userId, String pin, String scope, String jti, HttpServletRequest request) {
        tokenService.consumeStateToken(jti);

        if (loginAttemptService.isLocked(userId.toString())) {
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
        }

        loginAttemptService.applyProgressiveDelay(userId.toString());

        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (TokenScope.PIN_SETUP.getValue().equals(scope) && user.getStatus() == UserStatus.PENDING_VERIFICATION) {
            pinValidator.validate(pin);

            user.setUserPins(passwordEncoder.encode(pin));
            user.setStatus(UserStatus.ACTIVE);
            user.setPhoneVerified(true);
            userRepository.save(user);

            var userRegisteredEvent = DomainEventFactory.createUserRegisteredEvent(user);
            eventPublisher.publish("user-registered", user.getId().toString(), userRegisteredEvent);
        } else if (TokenScope.PIN_LOGIN.getValue().equals(scope) && user.getStatus() == UserStatus.ACTIVE) {
            if (!passwordEncoder.matches(pin, user.getUserPins())) {
                loginAttemptService.loginFailed(userId.toString());
                var attemptsLeft = loginAttemptService.getAttemptsLeft(userId.toString());
                throw new BusinessException(ErrorCode.INVALID_PIN, String.format("Invalid PIN. You have %d attempts left.", attemptsLeft));
            }
        } else {
            throw new BusinessException(ErrorCode.INVALID_TOKEN_SCOPE);
        }

        loginAttemptService.loginSucceeded(userId.toString());

        var ipAddress = Optional.ofNullable(request.getHeader("X-FORWARDED-FOR")).orElse(request.getRemoteAddr());
        var userAgent = request.getHeader("User-Agent");

        var tokenResponse = tokenService.generateTokens(user, ipAddress, userAgent);

        return ResponseBuilder.success("Authentication successful", tokenResponse, request);
    }
}