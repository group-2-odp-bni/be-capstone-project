package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class ValidationFailedException extends ApiException {
  public ValidationFailedException(String msg) {
    super(ErrorCode.VALIDATION_ERROR, msg, HttpStatus.BAD_REQUEST);
  }
}
