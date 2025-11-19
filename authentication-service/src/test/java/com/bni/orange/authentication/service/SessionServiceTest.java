package com.bni.orange.authentication.service;

import com.bni.orange.authentication.error.BusinessException;
import com.bni.orange.authentication.error.ErrorCode;
import com.bni.orange.authentication.model.entity.RefreshToken;
import com.bni.orange.authentication.model.entity.User;
import com.bni.orange.authentication.model.response.SessionResponse;
import com.bni.orange.authentication.repository.RefreshTokenRepository;
import com.bni.orange.authentication.repository.UserRepository;
import com.bni.orange.authentication.util.SecurityUtils;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionServiceTest {

    @InjectMocks
    private SessionService sessionService;

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private HttpServletRequest servletRequest;
    @Mock
    private User user;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(servletRequest.getRequestURI()).thenReturn("/api/v1/sessions/test");
        when(user.getId()).thenReturn(userId);
    }

    @Nested
    @DisplayName("getUserSessions Tests")
    class GetUserSessionsTests {

        private final String currentTokenString = "current-token";
        private final String currentTokenHash = SecurityUtils.hashToken(currentTokenString);

        @Test
        @DisplayName("Should return a list of active sessions")
        void getUserSessions_withValidData_shouldReturnSessionList() {
            var currentSessionToken = mock(RefreshToken.class);
            when(currentSessionToken.getId()).thenReturn(UUID.randomUUID());
            when(currentSessionToken.getTokenHash()).thenReturn(currentTokenHash);
            when(currentSessionToken.getIpAddress()).thenReturn("192.168.1.1");
            when(currentSessionToken.getUserAgent()).thenReturn("UserAgent1");
            when(currentSessionToken.getLastUsedAt()).thenReturn(Instant.now());

            var otherSessionToken = mock(RefreshToken.class);
            when(otherSessionToken.getId()).thenReturn(UUID.randomUUID());
            when(otherSessionToken.getTokenHash()).thenReturn("other-hash");
            when(otherSessionToken.getIpAddress()).thenReturn("192.168.1.2");
            when(otherSessionToken.getUserAgent()).thenReturn("UserAgent2");
            when(otherSessionToken.getLastUsedAt()).thenReturn(Instant.now().minusSeconds(3600));

            var tokenList = List.of(currentSessionToken, otherSessionToken);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(refreshTokenRepository.findAllByUserAndIsRevokedFalse(user)).thenReturn(tokenList);

            var response = sessionService.getUserSessions(userId, currentTokenString, servletRequest);

            assertNotNull(response.getData());
            assertEquals(2, response.getData().size());

            var currentSession = response.getData().stream().filter(SessionResponse::isCurrent).findFirst().orElse(null);
            var otherSession = response.getData().stream().filter(s -> !s.isCurrent()).findFirst().orElse(null);

            assertNotNull(currentSession);
            assertNotNull(otherSession);
            assertEquals(currentSessionToken.getId(), currentSession.sessionId());
            assertEquals(otherSessionToken.getId(), otherSession.sessionId());
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for null refresh token")
        void getUserSessions_withNullToken_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, 
                () -> sessionService.getUserSessions(userId, null, servletRequest));
        }

        @Test
        @DisplayName("Should throw IllegalArgumentException for blank refresh token")
        void getUserSessions_withBlankToken_shouldThrowException() {
            assertThrows(IllegalArgumentException.class, 
                () -> sessionService.getUserSessions(userId, "   ", servletRequest));
        }

        @Test
        @DisplayName("Should throw USER_NOT_FOUND when user does not exist")
        void getUserSessions_whenUserNotFound_shouldThrowException() {
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, 
                () -> sessionService.getUserSessions(userId, currentTokenString, servletRequest));

            assertEquals(ErrorCode.USER_NOT_FOUND, exception.getErrorCode());
        }
    }

    @Nested
    @DisplayName("terminateSession Tests")
    class TerminateSessionTests {

        private final UUID sessionId = UUID.randomUUID();

        @Test
        @DisplayName("Should revoke token for a valid session termination request")
        void terminateSession_withValidSession_shouldRevokeToken() {
            var tokenToTerminate = mock(RefreshToken.class);
            when(tokenToTerminate.getUser()).thenReturn(user);
            when(refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(tokenToTerminate));

            var response = sessionService.terminateSession(userId, sessionId, servletRequest);

            assertNotNull(response);
            assertEquals("Session terminated successfully", response.getMessage());
            verify(tokenToTerminate).setRevoked(true);
            verify(tokenToTerminate).setRevokedAt(any(Instant.class));
            verify(refreshTokenRepository).save(tokenToTerminate);
        }

        @Test
        @DisplayName("Should throw SESSION_NOT_FOUND when session does not exist")
        void terminateSession_whenSessionNotFound_shouldThrowException() {
            when(refreshTokenRepository.findById(sessionId)).thenReturn(Optional.empty());

            var exception = assertThrows(BusinessException.class, 
                () -> sessionService.terminateSession(userId, sessionId, servletRequest));

            assertEquals(ErrorCode.SESSION_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        @DisplayName("Should throw SESSION_TERMINATION_FORBIDDEN when user is not the owner")
        void terminateSession_whenUserNotOwner_shouldThrowException() {
            var otherUser = mock(User.class);
            var otherUserId = UUID.randomUUID();
            when(otherUser.getId()).thenReturn(otherUserId);

            var tokenToTerminate = mock(RefreshToken.class);
            when(tokenToTerminate.getUser()).thenReturn(otherUser);
            when(refreshTokenRepository.findById(sessionId)).thenReturn(Optional.of(tokenToTerminate));

            var exception = assertThrows(BusinessException.class, 
                () -> sessionService.terminateSession(userId, sessionId, servletRequest));

            assertEquals(ErrorCode.SESSION_TERMINATION_FORBIDDEN, exception.getErrorCode());
        }
    }
}
