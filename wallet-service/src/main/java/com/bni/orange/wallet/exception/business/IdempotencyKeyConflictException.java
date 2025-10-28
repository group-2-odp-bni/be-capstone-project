package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class IdempotencyKeyConflictException extends ApiException {
  public IdempotencyKeyConflictException(String msg) {
    super(ErrorCode.IDEMPOTENCY_CONFLICT, msg, HttpStatus.CONFLICT);
  }
}
