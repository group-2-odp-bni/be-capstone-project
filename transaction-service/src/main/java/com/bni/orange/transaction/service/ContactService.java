package com.bni.orange.transaction.service;

import com.bni.orange.transaction.client.UserServiceClient;
import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.request.VerifyContactRequest;
import com.bni.orange.transaction.model.response.PageResponse;
import com.bni.orange.transaction.model.response.QuickTransferResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;


@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final QuickTransferService quickTransferService;
    private final UserServiceClient userServiceClient;

    @Transactional(readOnly = true)
    public PageResponse<QuickTransferResponse> getContacts(UUID userId, int page, int size) {
        log.debug("Getting contacts for user: {}", userId);
        return quickTransferService.getQuickTransfers(userId, page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<QuickTransferResponse> searchContacts(UUID userId, String query, int page, int size) {
        log.debug("Searching contacts for user: {}, query: {}", userId, query);

        if (query == null || query.trim().length() < 2) {
            return PageResponse.empty(page, size);
        }

        return quickTransferService.searchQuickTransfers(userId, query.trim(), page, size);
    }

    @Transactional
    public QuickTransferResponse verifyAndAddContact(
        UUID userId,
        VerifyContactRequest request,
        String accessToken
    ) {
        log.info("Verifying and adding contact for user: {}, phone: {}", userId, request.phoneNumber());

        var userProfile = userServiceClient.findByPhoneNumber(request.phoneNumber(), accessToken)
            .block();

        if (userProfile == null) {
            log.warn("Phone number not registered: {}", request.phoneNumber());
            throw new BusinessException(
                ErrorCode.USER_NOT_FOUND,
                "This phone number is not registered in OrangePay"
            );
        }

        if (userProfile.id().equals(userId)) {
            log.warn("User {} trying to add themselves as contact", userId);
            throw new BusinessException(
                ErrorCode.INVALID_REQUEST,
                "You cannot add yourself as a contact"
            );
        }

        log.debug("Adding contact: userId={}, contactId={}, name={}",
            userId, userProfile.id(), userProfile.name());

        quickTransferService.addOrUpdateFromTransaction(
            userId,
            userProfile.id(),
            userProfile.name(),
            userProfile.phoneNumber()
        );

        return quickTransferService.getQuickTransferByRecipientId(userId, userProfile.id())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "Failed to retrieve added contact"
            ));
    }

    @Transactional
    public void removeContact(UUID userId, UUID contactId) {
        log.info("Removing contact: userId={}, contactId={}", userId, contactId);
        quickTransferService.removeQuickTransfer(userId, contactId);
    }
}
