package com.bni.orange.wallet.domain;

import com.bni.orange.wallet.model.enums.WalletMemberRole;
import com.bni.orange.wallet.model.enums.WalletStatus;
import com.bni.orange.wallet.model.enums.WalletType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class DomainEvents {
    @Getter 
    @Builder
    public static class WalletCreated {
        private final UUID walletId;
        private final UUID userId;
        private final WalletType type;
        private final WalletStatus status;
        private final String currency;
        private final String name;
        private final BigDecimal balanceSnapshot;
        private final boolean defaultForUser;
        private final OffsetDateTime createdAt;
        private final OffsetDateTime updatedAt;
    }

    @Getter
    @Builder
    public static class WalletUpdated {
        private final UUID walletId;
        private final UUID userId;
        private final WalletType type;
        private final WalletStatus status;
        private final String currency;
        private final String name;
        private final BigDecimal balanceSnapshot;
        private final OffsetDateTime updatedAt;
    }
    @Getter
    @Builder
    public static class WalletMemberInvited {
        private final UUID walletId;
        private final UUID inviterUserId;
        private final UUID invitedUserId;
        private final WalletMemberRole role;
        private final String walletName;
    }

    @Value @Builder
    public static class WalletInviteLinkGenerated {
        UUID walletId;
        UUID inviterUserId;      
        String phoneE164;        
        WalletMemberRole role;   
        String link;             
        String codeMasked;       
        String codePlain;        
        OffsetDateTime expiresAt;
        String nonce;            
    }

    @Value @Builder
    public static class WalletInviteAccepted {
        UUID walletId;
        UUID userId;              // yang menerima invite (current user)
        WalletMemberRole role;    // final role
        OffsetDateTime occurredAt;
    }
}