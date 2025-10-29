package com.bni.orange.authentication.service;

import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.request.PinChangeRequest;
import com.bni.orange.authentication.model.request.PinResetConfirmRequest;
import com.bni.orange.authentication.model.request.PinVerifyRequest;
import com.bni.orange.authentication.model.response.ApiResponse;
import com.bni.orange.authentication.model.response.PinVerifyResponse;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.util.ResponseBuilder;
import com.bni.orange.authentication.validator.PinValidator;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PinService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PinValidator pinValidator;

    @Transactional
    public ApiResponse<Void> changePin(UUID userId, PinChangeRequest request, HttpServletRequest servletRequest) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPin(), user.getUserPins())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PIN);
        }

        pinValidator.validate(request.newPin());

        user.setUserPins(passwordEncoder.encode(request.newPin()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUser(user);

        return ResponseBuilder.success("PIN changed successfully", servletRequest);
    }

    @Transactional
    public ApiResponse<Void> confirmPinReset(UUID userId, PinResetConfirmRequest request, HttpServletRequest servletRequest) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        pinValidator.validate(request.newPin());

        user.setUserPins(passwordEncoder.encode(request.newPin()));
        userRepository.save(user);

        return ResponseBuilder.success("PIN reset successfully", servletRequest);
    }

    @Transactional(readOnly = true)
    public ApiResponse<PinVerifyResponse> verifyPin(UUID userId, PinVerifyRequest request, HttpServletRequest servletRequest) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (user.getUserPins() == null || user.getUserPins().isEmpty()) {
            throw new BusinessException(ErrorCode.PIN_NOT_SET);
        }

        var response = passwordEncoder.matches(request.pin(), user.getUserPins())
            ? PinVerifyResponse.success()
            : PinVerifyResponse.failed();

        return ResponseBuilder.success("PIN reset successfully", response, servletRequest);
    }
}