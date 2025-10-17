package com.bni.orange.transaction.service;

import com.bni.orange.transaction.model.request.TransferRequest;
import com.bni.orange.transaction.model.response.TransferResponse;

public interface TransactionService {
    TransferResponse processTransfer(TransferRequest request);
}
