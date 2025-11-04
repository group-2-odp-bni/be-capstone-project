package com.bni.orange.transaction.controller;

import com.bni.orange.transaction.model.request.VerifyContactRequest;
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.PageResponse;
import com.bni.orange.transaction.model.response.QuickTransferResponse;
import com.bni.orange.transaction.service.ContactService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/contacts")
public class ContactController {

    private final ContactService contactService;

    @GetMapping
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<PageResponse<QuickTransferResponse>>> getContacts(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var contacts = contactService.getContacts(getUserIdFromJwt(jwt), page, size);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @GetMapping("/search")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<PageResponse<QuickTransferResponse>>> searchContacts(
        @RequestParam String q,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var contacts = contactService.searchContacts(getUserIdFromJwt(jwt), q, page, size);
        return ResponseEntity.ok(ApiResponse.success(contacts));
    }

    @PostMapping("/verify")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<QuickTransferResponse>> verifyAndAddContact(
        @Valid @RequestBody VerifyContactRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        var contact = contactService.verifyAndAddContact(getUserIdFromJwt(jwt), request, jwt.getTokenValue());
        return ResponseEntity.ok(ApiResponse.success(contact));
    }

    @DeleteMapping("/{contactId}")
    @PreAuthorize("hasAuthority('SCOPE_FULL_ACCESS')")
    public ResponseEntity<ApiResponse<Void>> removeContact(
        @PathVariable UUID contactId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        contactService.removeContact(getUserIdFromJwt(jwt), contactId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID getUserIdFromJwt(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
