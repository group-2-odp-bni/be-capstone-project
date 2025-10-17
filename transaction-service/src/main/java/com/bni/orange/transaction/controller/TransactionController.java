package com.bni.orange.transaction.controller;

//request
import com.bni.orange.transaction.model.request.TopupRequest;
import com.bni.orange.transaction.model.request.TransferRequest;

//response
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.InquiryResponse;
import com.bni.orange.transaction.model.response.TopupResponse;
import com.bni.orange.transaction.model.response.TransferResponse;

import com.bni.orange.transaction.service.TransactionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;


@Controller
@RequiredArgsConstructor
@RequestMapping("/api/v1/transaction")
public class TransactionController {

    private final TransactionService transactionService;

    @GetMapping
    public ResponseEntity<ApiResponse<Object>> test(){
        return ResponseEntity.ok(new ApiResponse<>("200", "Inquiry success", null));
    }


    @PostMapping("/inquiry")
    public ResponseEntity<ApiResponse<InquiryResponse>> inquiry( TransferRequest request) {

        InquiryResponse response = null;


        if(response == null){
            return ResponseEntity
                    .status(404)
                    .body(new ApiResponse<>("404", "User not found", null));
        }

        return ResponseEntity.ok(new ApiResponse<>("200", "Inquiry success", response)
                );
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(@RequestBody @Valid TransferRequest request) {
        TransferResponse response = transactionService.processTransfer(request);

        return ResponseEntity.ok(
                new ApiResponse<>(null, "Transfer Success", response)
        );
    }

    @PostMapping("/topup")
    public ResponseEntity<ApiResponse<TopupResponse>> topup(@RequestBody @Valid TopupRequest request) {

        TopupResponse response = null;

        if(response == null){
            return ResponseEntity.status(404).body(new ApiResponse<>("TRX-003", "User not found", null));
        }

        return ResponseEntity
                .ok(new ApiResponse<>(null, "Transfer Success", null)
                );
    }



}
