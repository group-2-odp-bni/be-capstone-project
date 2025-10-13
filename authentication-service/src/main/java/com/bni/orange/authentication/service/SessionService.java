package com.bni.orange.authentication.service;

import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.response.SessionResponse;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional(readOnly = true)
    public List<SessionResponse> getUserSessions(UUID userId, String currentRefreshToken) {
        if (currentRefreshToken == null || currentRefreshToken.isBlank()) {
            throw new IllegalArgumentException("Current refresh token is required");
        }

        var user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        final var currentTokenHash = SecurityUtils.hashToken(currentRefreshToken);

        return refreshTokenRepository.findAllByUserAndIsRevokedFalse(user).stream()
            .map(token -> SessionResponse.builder()
                .sessionId(token.getId())
                .ipAddress(token.getIpAddress())
                .userAgent(token.getUserAgent())
                .lastUsedAt(token.getLastUsedAt())
                .isCurrent(token.getTokenHash().equals(currentTokenHash))
                .build()
            )
            .collect(Collectors.toList());
    }

    @Transactional
    public void terminateSession(UUID userId, UUID sessionId) {
        var token = refreshTokenRepository.findById(sessionId)
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));

        if (!token.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.SESSION_TERMINATION_FORBIDDEN);
        }

        token.setRevoked(true);
        token.setRevokedAt(java.time.Instant.now());
        refreshTokenRepository.save(token);
    }
}