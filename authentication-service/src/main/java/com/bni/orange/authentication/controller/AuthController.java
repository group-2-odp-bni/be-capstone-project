package com.bni.orange.authentication.controller;

import com.bni.orange.authentication.model.request.RegisterRequest;
import com.bni.orange.authentication.model.response.RegisterResponse;
import com.bni.orange.authentication.service.RegisterService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterService registerService;

    @PostMapping("/register")
    public RegisterResponse register(@RequestBody @Valid RegisterRequest request) {
        return registerService.execute(request);
    }
}
