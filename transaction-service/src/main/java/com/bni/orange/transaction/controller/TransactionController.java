package com.bni.orange.transaction.controller;

//request
import com.bni.orange.transaction.model.request.TransferRequest;

//response
import com.bni.orange.transaction.model.response.ApiResponse;
import com.bni.orange.transaction.model.response.InquiryResponse;
import com.bni.orange.transaction.model.response.TransferResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequiredArgsConstructor
@RequestMapping("/trx")
public class TransactionController {


    @PostMapping("/inquiry")
    public ResponseEntity<ApiResponse<InquiryResponse>> inquiry( TransferRequest request) {


        return 0;
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<TransferResponse>> transfer(@RequestBody @Valid TransferRequest request) {


        return 0;
    }



}
