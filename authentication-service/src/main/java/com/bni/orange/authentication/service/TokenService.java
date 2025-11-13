package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.JwtProperties;
import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.entity.RefreshToken;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.model.enums.TokenScope;
import com.bni.orange.authentication.model.response.ApiResponse;
import com.bni.orange.authentication.model.response.TokenResponse;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.util.ResponseBuilder;
import com.bni.orange.authentication.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import static com.bni.orange.authentication.util.SecurityUtils.hashToken;

@Service
@RequiredArgsConstructor
public class TokenService {

    private static final SecureRandom secureRandom = new SecureRandom();
    private static final Base64.Encoder base64Encoder = Base64.getUrlEncoder().withoutPadding();
    private final JwtEncoder jwtEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BlacklistService blacklistService;
    private final JwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedisPrefixProperties redisProperties;

    public String generateStateToken(User user, String scope) {
        var now = Instant.now();
        var jti = java.util.UUID.randomUUID().toString();

        var stateTokenKey = redisProperties.prefix().stateToken() + jti;
        redisTemplate.opsForValue().set(stateTokenKey, "active", jwtProperties.stateTokenDuration());

        var claims = JwtClaimsSet.builder()
            .issuer("auth-service")
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.stateTokenDuration()))
            .subject(user.getId().toString())
            .id(jti)
            .claim("scope", scope)
            .claim("type", "state")
            .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    public void consumeStateToken(String jti) {
        var stateTokenKey = redisProperties.prefix().stateToken() + jti;
        var status = redisTemplate.opsForValue().get(stateTokenKey);

        if (Objects.isNull(status)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN_SCOPE, "State token has expired or already been used");
        }

        if (!"active".equals(status)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN_SCOPE, "State token has already been used");
        }

        redisTemplate.delete(stateTokenKey);
    }


    @Transactional
    public ApiResponse<TokenResponse> rotateRefreshToken(String oldRefreshTokenString, HttpServletRequest request) {
        var oldTokenHash = SecurityUtils.hashToken(oldRefreshTokenString);

        var activeTokenOpt = refreshTokenRepository.findByTokenHashAndIsRevokedFalse(oldTokenHash);

        if (activeTokenOpt.isPresent()) {
            var refreshToken = activeTokenOpt.get();

            if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
                refreshTokenRepository.delete(refreshToken);
                throw new BusinessException(ErrorCode.REFRESH_TOKEN_EXPIRED);
            }

            refreshToken.setRevoked(true);
            refreshToken.setRevokedAt(Instant.now());
            refreshTokenRepository.save(refreshToken);

            var user = refreshToken.getUser();
            var ipAddress = Optional.ofNullable(request.getHeader("X-FORWARDED-FOR")).orElse(request.getRemoteAddr());
            var userAgent = request.getHeader("User-Agent");

            var tokenResponse = generateTokens(user, ipAddress, userAgent);
            return ResponseBuilder.success("Token refreshed successfully", tokenResponse, request);
        }

        // TODO --- SECURITY ALERT ---
        // If the active token is not found, check whether this token once existed but has been revoked.
        // If so, this indicates TOKEN REUSE (potential token theft).

        refreshTokenRepository
            .findByTokenHash(oldTokenHash)
            .ifPresent(refreshToken -> refreshTokenRepository.revokeAllByUser(refreshToken.getUser()));

        throw new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED);
    }

    @Transactional
    public ApiResponse<Void> logout(Jwt jwt, String refreshTokenString, HttpServletRequest request) {
        var jti = jwt.getId();
        var expiresAt = jwt.getExpiresAt();
        if (expiresAt != null) {
            var validityDuration = Duration.between(Instant.now(), expiresAt);
            if (!validityDuration.isNegative()) {
                blacklistService.blacklistToken(jti, validityDuration);
            }
        }

        if (refreshTokenString != null && !refreshTokenString.isBlank()) {
            var tokenHash = hashToken(refreshTokenString);
            refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(refreshTokenRepository::delete);
        }

        return ResponseBuilder.success("Successfully logged out", request);
    }

    public TokenResponse generateTokens(User user, String ipAddress, String userAgent) {
        var accessToken = generateAccessToken(user);
        var refreshTokenString = generateRefreshToken();

        var refreshToken = RefreshToken.builder()
            .user(user)
            .tokenHash(hashToken(refreshTokenString))
            .expiryDate(Instant.now().plus(jwtProperties.refreshTokenDuration()))
            .ipAddress(ipAddress)
            .userAgent(userAgent)
            .lastUsedAt(Instant.now())
            .build();
        refreshTokenRepository.save(refreshToken);

        return TokenResponse.builder()
            .accessToken(accessToken)
            .refreshToken(refreshTokenString)
            .expiresIn(jwtProperties.accessTokenDuration().toSeconds())
            .build();
    }

    private String generateAccessToken(User user) {
        var now = Instant.now();
        var jti = java.util.UUID.randomUUID().toString();
        var claims = JwtClaimsSet.builder()
            .issuer("auth-service")
            .issuedAt(now)
            .expiresAt(now.plus(jwtProperties.accessTokenDuration()))
            .subject(user.getId().toString())
            .id(jti)
            .claim("scope", TokenScope.FULL_ACCESS.getValue())
            .claim("type", "access")
            .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return base64Encoder.encodeToString(randomBytes);
    }
}