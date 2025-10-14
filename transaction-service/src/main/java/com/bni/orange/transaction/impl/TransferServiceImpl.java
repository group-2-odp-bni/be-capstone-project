package com.bni.orange.transaction.impl;

import com.bni.orange.transaction.model.entity.TransferOltpEntity;
import com.bni.orange.transaction.model.enums.TxStatus;
import com.bni.orange.transaction.model.enums.TxType;
import com.bni.orange.transaction.model.request.TransferRequest;
import com.bni.orange.transaction.model.response.TransferResponse;
import com.bni.orange.transaction.repository.TransferOltpRepository;
import com.bni.orange.transaction.service.TransferService;
import com.bni.orange.transaction.utils.TransactionIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.stream.DoubleStream;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final TransferOltpRepository transferOltpRepository;

    @Override
    public TransferResponse processTransfer(TransferRequest request) {
        // build entity
        String trxId = TransactionIdGenerator.generate(request.walletId());

        TransferOltpEntity entity = new TransferOltpEntity();
        entity.setId(UUID.randomUUID());
        entity.setWalletId(request.walletId());
        entity.setTrxId(trxId);
        entity.setType(TxType.TRANSFER);
        entity.setAmount(request.amount());
        entity.setStatus(TxStatus.PENDING);
        entity.setInitiatedBy(request.senderId());

        // save to DB
        TransferOltpEntity saved = transferOltpRepository.save(entity);

        // build response
        return new TransferResponse(
                saved.getWalletId(),
                request.receiverId(),
                saved.getTrxId(),
                saved.getType(),
                saved.getAmount(),
                saved.getStatus()
        );
    }
}
