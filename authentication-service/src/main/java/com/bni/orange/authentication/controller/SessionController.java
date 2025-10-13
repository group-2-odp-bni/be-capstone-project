package com.bni.orange.authentication.controller;

import com.bni.orange.authentication.model.response.SessionResponse;
import com.bni.orange.authentication.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sessions")
@PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
public class SessionController {

    private final SessionService sessionService;

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getActiveSessions(
        Authentication authentication,
        @RequestHeader("X-Refresh-Token") String currentRefreshToken
    ) {
        var userId = UUID.fromString(authentication.getName());
        return ResponseEntity.ok(sessionService.getUserSessions(userId, currentRefreshToken));
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<?> terminateSession(Authentication authentication, @PathVariable UUID sessionId) {
        var userId = UUID.fromString(authentication.getName());
        sessionService.terminateSession(userId, sessionId);
        return ResponseEntity.ok().build();
    }
}