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

    public static Specification<Transaction> buildSpecification(
        UUID userId,
        UUID walletId,
        TransactionStatus status,
        OffsetDateTime startDate,
        OffsetDateTime endDate
    ) {
        if (walletId != null) {
            return belongsToWallet(walletId)
                .and(hasStatus(status))
                .and(createdBetween(startDate, endDate));
        } else {
            return belongsToUser(userId)
                .and(hasStatus(status))
                .and(createdBetween(startDate, endDate));
        }
    }

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
            // Each user has their own transaction record (TRANSFER_OUT for sender, TRANSFER_IN for receiver)
            return criteriaBuilder.equal(root.get("userId"), userId);
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

    public static Specification<Transaction> belongsToWallet(UUID walletId) {
        return (root, query, criteriaBuilder) -> {
            if (walletId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("walletId"), walletId);
        };
    }

    public static Specification<Transaction> belongsToWallets(List<UUID> walletIds) {
        return (root, query, criteriaBuilder) -> {
            if (walletIds == null || walletIds.isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return root.get("walletId").in(walletIds);
        };
    }
}
