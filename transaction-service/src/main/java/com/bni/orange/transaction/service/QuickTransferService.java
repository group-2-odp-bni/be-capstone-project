package com.bni.orange.transaction.service;

import com.bni.orange.transaction.error.BusinessException;
import com.bni.orange.transaction.error.ErrorCode;
import com.bni.orange.transaction.model.entity.QuickTransfer;
import com.bni.orange.transaction.model.request.QuickTransferAddRequest;
import com.bni.orange.transaction.model.response.QuickTransferResponse;
import com.bni.orange.transaction.repository.QuickTransferRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;


@Slf4j
@Service
@RequiredArgsConstructor
public class QuickTransferService {

    private final QuickTransferRepository quickTransferRepository;

    private record SortConfig(
        String field,
        Sort.Direction direction
    ) {
    }

    @Transactional(readOnly = true)
    public List<QuickTransferResponse> getQuickTransfers(UUID userId, String orderBy, String search) {
        Objects.requireNonNull(userId, "userId cannot be null");

        var sortConfig = switch (Optional.ofNullable(orderBy).map(String::toLowerCase).orElse("usage")) {
            case "recent" -> new SortConfig("lastUsedAt", Sort.Direction.DESC);
            case "order" -> new SortConfig("displayOrder", Sort.Direction.ASC);
            default -> new SortConfig("usageCount", Sort.Direction.DESC);
        };

        var sort = Sort.by(sortConfig.direction(), sortConfig.field());
        var hasSearch = search != null && !search.isBlank();

        Supplier<List<QuickTransfer>> query = hasSearch
            ? () -> quickTransferRepository.findByUserIdAndSearchTerm(userId, search.trim(), sort)
            : switch (sortConfig.field()) {
            case "lastUsedAt" -> () -> quickTransferRepository.findByUserIdOrderByLastUsedAtDesc(userId);
            case "displayOrder" -> () -> quickTransferRepository.findByUserIdOrderByDisplayOrderAsc(userId);
            default -> () -> quickTransferRepository.findByUserIdOrderByUsageCountDesc(userId);
        };

        return query.get().stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<QuickTransferResponse> getTopQuickTransfers(UUID userId, int limit) {
        var quickTransfers = quickTransferRepository.findTopByUserId(userId);

        return quickTransfers.stream().limit(limit)
            .map(this::toResponse)
            .toList();
    }

    @Transactional
    public QuickTransferResponse addQuickTransfer(UUID userId, QuickTransferAddRequest request) {
        if (userId.equals(request.recipientUserId())) {
            throw new BusinessException(ErrorCode.SELF_TRANSFER_NOT_ALLOWED, "Cannot add yourself as quick transfer");
        }

        if (quickTransferRepository.existsByUserIdAndRecipientUserId(userId, request.recipientUserId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_TRANSACTION, "This recipient is already in your quick transfers");
        }

        var currentCount = quickTransferRepository.countByUserId(userId);

        var quickTransfer = QuickTransfer.builder()
            .userId(userId)
            .recipientUserId(request.recipientUserId())
            .recipientName(request.recipientName())
            .recipientPhone(request.recipientPhone())
            .recipientAvatarInitial(QuickTransfer.getAvatarInitial(request.recipientName()))
            .usageCount(0)
            .displayOrder((int) currentCount)
            .build();

        var saved = quickTransferRepository.save(quickTransfer);
        log.info("Quick transfer added: {} for user: {}", saved.getId(), userId);

        return toResponse(saved);
    }

    @Transactional
    public void addOrUpdateFromTransaction(UUID userId, UUID recipientUserId, String recipientName, String recipientPhone) {
        quickTransferRepository.findByUserIdAndRecipientUserId(userId, recipientUserId)
            .ifPresentOrElse(
                this::incrementQuickTransferUsage,
                () -> createNewQuickTransfer(userId, recipientUserId, recipientName, recipientPhone)
            );
    }

    @Transactional
    public void removeQuickTransfer(UUID userId, UUID recipientUserId) {
        var quickTransfer = quickTransferRepository
            .findByUserIdAndRecipientUserId(userId, recipientUserId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Quick transfer not found"));

        quickTransferRepository.delete(quickTransfer);
        log.info("Quick transfer removed: {}", quickTransfer.getId());
    }

    @Transactional
    public void updateDisplayOrder(UUID userId, UUID quickTransferId, int newOrder) {
        var quickTransfer = quickTransferRepository.findById(quickTransferId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Quick transfer not found"));

        if (!quickTransfer.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND, "Quick transfer not found or you don\'t have permission");
        }

        quickTransfer.setDisplayOrder(newOrder);
        quickTransferRepository.save(quickTransfer);
    }

    private void incrementQuickTransferUsage(QuickTransfer existing) {
        existing.incrementUsage();
        quickTransferRepository.save(existing);
        log.debug("Quick transfer usage incremented: {}", existing.getId());
    }

    private void createNewQuickTransfer(UUID userId, UUID recipientUserId, String recipientName, String recipientPhone) {
        var currentCount = quickTransferRepository.countByUserId(userId);

        var newQuickTransfer = QuickTransfer.builder()
            .userId(userId)
            .recipientUserId(recipientUserId)
            .recipientName(recipientName)
            .recipientPhone(recipientPhone)
            .recipientAvatarInitial(QuickTransfer.getAvatarInitial(recipientName))
            .usageCount(1)
            .displayOrder((int) currentCount)
            .build();

        newQuickTransfer.setLastUsedAt(OffsetDateTime.now());

        quickTransferRepository.save(newQuickTransfer);
        log.info("New quick transfer auto-added for user: {}", userId);
    }

    private QuickTransferResponse toResponse(QuickTransfer quickTransfer) {
        return QuickTransferResponse.builder()
            .id(quickTransfer.getId())
            .userId(quickTransfer.getUserId())
            .recipientUserId(quickTransfer.getRecipientUserId())
            .recipientName(quickTransfer.getRecipientName())
            .recipientPhone(quickTransfer.getRecipientPhone())
            .recipientAvatarInitial(quickTransfer.getRecipientAvatarInitial())
            .lastUsedAt(quickTransfer.getLastUsedAt())
            .usageCount(quickTransfer.getUsageCount())
            .displayOrder(quickTransfer.getDisplayOrder())
            .createdAt(quickTransfer.getCreatedAt())
            .build();
    }
}