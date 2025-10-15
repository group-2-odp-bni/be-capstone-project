package com.bni.orange.authentication.service;

import com.bni.orange.authentication.config.properties.JwtProperties;
import com.bni.orange.authentication.config.properties.RedisPrefixProperties;
import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.entity.RefreshToken;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.util.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenServiceTest {

    @InjectMocks
    private TokenService tokenService;

    @Mock
    private JwtEncoder jwtEncoder;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private BlacklistService blacklistService;
    @Mock
    private JwtProperties jwtProperties;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisPrefixProperties redisProperties;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private User user;
    @Mock
    private Jwt jwt;

    private final UUID userId = UUID.randomUUID();
    private final String stateTokenPrefix = "state_token:";

    @BeforeEach
    void setUp() {
        when(user.getId()).thenReturn(userId);
        when(redisProperties.prefix()).thenReturn(mock(RedisPrefixProperties.Prefix.class));
        when(redisProperties.prefix().stateToken()).thenReturn(stateTokenPrefix);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(jwtProperties.stateTokenDuration()).thenReturn(Duration.ofMinutes(5));
        when(jwtProperties.accessTokenDuration()).thenReturn(Duration.ofMinutes(15));
        when(jwtProperties.refreshTokenDuration()).thenReturn(Duration.ofDays(30));

        var mockJwt = mock(org.springframework.security.oauth2.jwt.Jwt.class);
        when(mockJwt.getTokenValue()).thenReturn("encoded-jwt-string");
        when(jwtEncoder.encode(any(JwtEncoderParameters.class))).thenReturn(mockJwt);
    }

    @Nested
    @DisplayName("State Token Tests")
    class StateTokenTests {
        @Test
        @DisplayName("generateStateToken should set key in Redis and return encoded token")
        void generateStateToken_shouldSetKeyAndEncode() {
            var scope = "TEST_SCOPE";
            var token = tokenService.generateStateToken(user, scope);

            verify(valueOperations).set(contains(stateTokenPrefix), eq("active"), eq(Duration.ofMinutes(5)));
            verify(jwtEncoder).encode(any(JwtEncoderParameters.class));
            assertEquals("encoded-jwt-string", token);
        }

        @Test
        @DisplayName("consumeStateToken should delete a valid token")
        void consumeStateToken_withValidToken_shouldDeleteKey() {
            var jti = "valid-jti";
            when(valueOperations.get(stateTokenPrefix + jti)).thenReturn("active");

            assertDoesNotThrow(() -> tokenService.consumeStateToken(jti));
            verify(redisTemplate).delete(stateTokenPrefix + jti);
        }

        @Test
        @DisplayName("consumeStateToken should throw if token is not found")
        void consumeStateToken_whenTokenNotFound_shouldThrow() {
            var jti = "not-found-jti";
            when(valueOperations.get(stateTokenPrefix + jti)).thenReturn(null);

            var ex = assertThrows(BusinessException.class, () -> tokenService.consumeStateToken(jti));
            assertEquals(ErrorCode.INVALID_TOKEN_SCOPE, ex.getErrorCode());
        }

        @Test
        @DisplayName("consumeStateToken should throw if token is not active")
        void consumeStateToken_whenTokenNotActive_shouldThrow() {
            var jti = "used-jti";
            when(valueOperations.get(stateTokenPrefix + jti)).thenReturn("used");

            var ex = assertThrows(BusinessException.class, () -> tokenService.consumeStateToken(jti));
            assertEquals(ErrorCode.INVALID_TOKEN_SCOPE, ex.getErrorCode());
        }
    }

    @Nested
    @DisplayName("rotateRefreshToken Tests")
    class RotateRefreshTokenTests {
        private final String oldTokenString = "old-refresh-token";
        private final String oldTokenHash = SecurityUtils.hashToken(oldTokenString);

        @Test
        @DisplayName("Should rotate token successfully for valid, active token")
        void rotate_withValidToken_shouldSucceed() {
            var refreshToken = mock(RefreshToken.class);
            when(refreshToken.getExpiryDate()).thenReturn(Instant.now().plus(Duration.ofDays(1)));
            when(refreshToken.getUser()).thenReturn(user);
            when(refreshTokenRepository.findByTokenHashAndIsRevokedFalse(oldTokenHash)).thenReturn(Optional.of(refreshToken));

            var response = tokenService.rotateRefreshToken(oldTokenString, servletRequest);

            verify(refreshToken).setRevoked(true);
            verify(refreshToken).setRevokedAt(any(Instant.class));

            verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
            verify(refreshTokenRepository).save(eq(refreshToken));

            assertEquals("Token refreshed successfully", response.getMessage());
        }

        @Test
        @DisplayName("Should throw REFRESH_TOKEN_EXPIRED for expired token")
        void rotate_withExpiredToken_shouldThrowAndDelet() {
            var refreshToken = mock(RefreshToken.class);
            when(refreshToken.getExpiryDate()).thenReturn(Instant.now().minus(Duration.ofDays(1)));
            when(refreshTokenRepository.findByTokenHashAndIsRevokedFalse(oldTokenHash)).thenReturn(Optional.of(refreshToken));

            var ex = assertThrows(BusinessException.class, () -> tokenService.rotateRefreshToken(oldTokenString, servletRequest));

            assertEquals(ErrorCode.REFRESH_TOKEN_EXPIRED, ex.getErrorCode());
            verify(refreshTokenRepository).delete(refreshToken);
        }

        @Test
        @DisplayName("Should throw TOKEN_REUSE_DETECTED for reused token and revoke all")
        void rotate_withReusedToken_shouldThrowAndRevokeAll() {
            var revokedToken = mock(RefreshToken.class);
            when(revokedToken.getUser()).thenReturn(user);
            when(refreshTokenRepository.findByTokenHashAndIsRevokedFalse(oldTokenHash)).thenReturn(Optional.empty());
            when(refreshTokenRepository.findByTokenHash(oldTokenHash)).thenReturn(Optional.of(revokedToken));

            var ex = assertThrows(BusinessException.class, () -> tokenService.rotateRefreshToken(oldTokenString, servletRequest));

            assertEquals(ErrorCode.TOKEN_REUSE_DETECTED, ex.getErrorCode());
            verify(refreshTokenRepository).revokeAllByUser(user);
        }

        @Test
        @DisplayName("Should throw TOKEN_REUSE_DETECTED for non-existent token")
        void rotate_withNonExistentToken_shouldThrow() {
            when(refreshTokenRepository.findByTokenHashAndIsRevokedFalse(oldTokenHash)).thenReturn(Optional.empty());
            when(refreshTokenRepository.findByTokenHash(oldTokenHash)).thenReturn(Optional.empty());

            var ex = assertThrows(BusinessException.class, () -> tokenService.rotateRefreshToken(oldTokenString, servletRequest));

            assertEquals(ErrorCode.TOKEN_REUSE_DETECTED, ex.getErrorCode());
            verify(refreshTokenRepository, never()).revokeAllByUser(any());
        }
    }

    @Nested
    @DisplayName("Logout Tests")
    class LogoutTests {
        private final String jti = "jwt-id";

        @Test
        @DisplayName("Should blacklist access token and delete refresh token")
        void logout_withAllTokens_shouldBlacklistAndDelete() {
            when(jwt.getId()).thenReturn(jti);
            when(jwt.getExpiresAt()).thenReturn(Instant.now().plus(Duration.ofMinutes(5)));
            var refreshToken = mock(RefreshToken.class);
            when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));

            var refreshTokenString = "refresh-token-to-delete";
            tokenService.logout(jwt, refreshTokenString, servletRequest);

            verify(blacklistService).blacklistToken(eq(jti), any(Duration.class));
            verify(refreshTokenRepository).delete(refreshToken);
        }

        @Test
        @DisplayName("Should not blacklist expired access token")
        void logout_withExpiredAccessToken_shouldNotBlacklist() {
            when(jwt.getId()).thenReturn(jti);
            when(jwt.getExpiresAt()).thenReturn(Instant.now().minus(Duration.ofMinutes(5)));

            tokenService.logout(jwt, null, servletRequest);

            verify(blacklistService, never()).blacklistToken(anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("Should handle null refresh token gracefully")
        void logout_withNullRefreshToken_shouldOnlyBlacklist() {
            when(jwt.getId()).thenReturn(jti);
            when(jwt.getExpiresAt()).thenReturn(Instant.now().plus(Duration.ofMinutes(5)));

            tokenService.logout(jwt, null, servletRequest);

            verify(blacklistService).blacklistToken(eq(jti), any(Duration.class));
            verify(refreshTokenRepository, never()).findByTokenHash(anyString());
        }
    }

    @Test
    @DisplayName("generateTokens should save a new refresh token and return a TokenResponse")
    void generateTokens_shouldSaveTokenAndReturnResponse() {
        var ipAddress = "127.0.0.1";
        var userAgent = "Test Agent";

        var response = tokenService.generateTokens(user, ipAddress, userAgent);

        var tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(tokenCaptor.capture());

        var savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertEquals(ipAddress, savedToken.getIpAddress());
        assertEquals(userAgent, savedToken.getUserAgent());

        assertEquals("encoded-jwt-string", response.accessToken());
        assertNotNull(response.refreshToken());
        assertTrue(response.expiresIn() > 0);
    }
}
