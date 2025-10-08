package com.bni.orange.transaction.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/transaction")
public class InquiryController {

//  return object
    private final InquiryResponse inquiryResponse;
    private final TransferResponse transferResponse;

//  post method
    @PostMapping("/inqury")
    public InquiryResponse inquiry(@RequestBody @Valid InquiryRequest request) {
        return 0;
    }

    @PostMapping("/transfer")
    public TransferResponse transfer(@RequestBody @Valid TransferRequest request) {
        return 0;
    }
}
