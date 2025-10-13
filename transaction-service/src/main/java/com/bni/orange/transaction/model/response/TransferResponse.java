package com.bni.orange.transaction.model.response;
import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferResponse(
        UUID senderWalletId,
        UUID ReceiverWalletId,
        String trxId,
        TxType type,
        BigDecimal amount,
        TxStatus status

) {

}

