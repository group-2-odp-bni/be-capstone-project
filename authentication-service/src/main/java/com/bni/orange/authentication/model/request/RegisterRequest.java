package com.bni.orange.authentication.model.request;

import com.bni.orange.authentication.base.Request;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder
public record RegisterRequest(
    @NotBlank
    String phoneNumber
) implements Request {
}
