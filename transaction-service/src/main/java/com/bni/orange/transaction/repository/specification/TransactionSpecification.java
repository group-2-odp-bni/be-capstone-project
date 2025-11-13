package com.bni.orange.transaction.repository.specification;

import com.bni.orange.transaction.model.entity.Transaction;
import com.bni.orange.transaction.model.enums.TransactionStatus;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;


@UtilityClass
public class TransactionSpecification {

    public static Specification<Transaction> buildSpecification(
        UUID userId,
        TransactionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate
    ) {
        return belongsToUser(userId)
            .and(hasStatus(status))
            .and(createdBetween(startDate, endDate));
    }

    /**
     * Enhanced specification builder with wallet filtering support.
     * Used for multi-wallet transaction history filtering.
     */
    public static Specification<Transaction> buildSpecification(
        UUID userId,
        UUID walletId,
        TransactionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate
    ) {
        if (walletId != null) {
            // When walletId is specified, filter by that specific wallet
            return belongsToWallet(walletId)
                .and(hasStatus(status))
                .and(createdBetween(startDate, endDate));
        } else {
            // When walletId is not specified, filter by userId (legacy behavior)
            return belongsToUser(userId)
                .and(hasStatus(status))
                .and(createdBetween(startDate, endDate));
        }
    }

    /**
     * Enhanced specification builder with multi-wallet support.
     * Filters transactions belonging to any of the user's accessible wallets.
     */
    public static Specification<Transaction> buildSpecificationForUserWallets(
        List<UUID> walletIds,
        TransactionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate
    ) {
        return belongsToWallets(walletIds)
            .and(hasStatus(status))
            .and(createdBetween(startDate, endDate));
    }

    public static Specification<Transaction> belongsToUser(UUID userId) {
        return (root, query, criteriaBuilder) -> {
            if (userId == null) {
                return criteriaBuilder.conjunction();
            }
            var senderPredicate = criteriaBuilder.equal(root.get("senderUserId"), userId);
            var receiverPredicate = criteriaBuilder.equal(root.get("receiverUserId"), userId);
            return criteriaBuilder.or(senderPredicate, receiverPredicate);
        };
    }

    public static Specification<Transaction> hasStatus(TransactionStatus status) {
        return (root, query, criteriaBuilder) ->
            status == null ? criteriaBuilder.conjunction() :
                criteriaBuilder.equal(root.get("status"), status);
    }

    public static Specification<Transaction> createdBetween(OffsetDateTime startDate, OffsetDateTime endDate) {
        return (root, query, criteriaBuilder) -> {
            if (startDate != null && endDate != null) {
                return criteriaBuilder.between(root.get("createdAt"), startDate, endDate);
            }
            if (startDate != null) {
                return criteriaBuilder.greaterThanOrEqualTo(root.get("createdAt"), startDate);
            }
            if (endDate != null) {
                return criteriaBuilder.lessThanOrEqualTo(root.get("createdAt"), endDate);
            }
            return criteriaBuilder.conjunction();
        };
    }

    /**
     * Filters transactions where the specified wallet is either sender or receiver.
     * Used for wallet-specific transaction history.
     */
    public static Specification<Transaction> belongsToWallet(UUID walletId) {
        return (root, query, criteriaBuilder) -> {
            if (walletId == null) {
                return criteriaBuilder.conjunction();
            }
            var senderWalletPredicate = criteriaBuilder.equal(root.get("senderWalletId"), walletId);
            var receiverWalletPredicate = criteriaBuilder.equal(root.get("receiverWalletId"), walletId);
            return criteriaBuilder.or(senderWalletPredicate, receiverWalletPredicate);
        };
    }

    /**
     * Filters transactions where any of the specified wallets is either sender or receiver.
     * Used when displaying transaction history across all user's accessible wallets.
     */
    public static Specification<Transaction> belongsToWallets(List<UUID> walletIds) {
        return (root, query, criteriaBuilder) -> {
            if (walletIds == null || walletIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            var senderWalletPredicate = root.get("senderWalletId").in(walletIds);
            var receiverWalletPredicate = root.get("receiverWalletId").in(walletIds);
            return criteriaBuilder.or(senderWalletPredicate, receiverWalletPredicate);
        };
    }
}
