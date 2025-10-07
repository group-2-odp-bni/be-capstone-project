package com.bni.orange.authentication.service;

import com.bni.orange.authentication.base.BaseService;
import com.bni.orange.authentication.model.request.RegisterRequest;
import com.bni.orange.authentication.model.response.RegisterResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegisterService implements BaseService<RegisterRequest, RegisterResponse> {

    @Override
    public RegisterResponse execute(RegisterRequest request) {
        return null;
    }
}
