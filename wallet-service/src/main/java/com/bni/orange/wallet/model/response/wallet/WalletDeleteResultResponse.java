package com.bni.orange.wallet.model.response.wallet;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
public class WalletDeleteResultResponse {

    private UUID walletId;              
    private UUID destinationWalletId;   
    private BigDecimal balanceMoved;    
    private String message;
}
