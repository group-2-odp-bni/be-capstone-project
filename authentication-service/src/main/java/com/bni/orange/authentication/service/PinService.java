package com.bni.orange.authentication.service;

import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.request.PinChangeRequest;
import com.bni.orange.authentication.model.request.PinResetConfirmRequest;
import com.bni.orange.authentication.model.response.MessageResponse;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.validator.PinValidator;
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
    public MessageResponse changePin(UUID userId, PinChangeRequest request) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.currentPin(), user.getUserPins())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PIN);
        }

        pinValidator.validate(request.newPin());

        user.setUserPins(passwordEncoder.encode(request.newPin()));
        userRepository.save(user);

        refreshTokenRepository.revokeAllByUser(user);

        return MessageResponse.builder()
            .message("PIN has been successfully changed. All other sessions have been logged out.")
            .build();
    }

    @Transactional
    public MessageResponse confirmPinReset(UUID userId, PinResetConfirmRequest request) {
        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        pinValidator.validate(request.newPin());

        user.setUserPins(passwordEncoder.encode(request.newPin()));
        userRepository.save(user);

        return MessageResponse.builder()
            .message("PIN has been successfully reset.")
            .build();
    }
}