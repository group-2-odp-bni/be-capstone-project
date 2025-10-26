package com.bni.orange.transaction.model.response.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final Object details;
    private final List<ValidationError> validationErrors;

    @Getter
    @RequiredArgsConstructor
    public static class ValidationError {
        private final String field;
        private final String message;
    }
}
