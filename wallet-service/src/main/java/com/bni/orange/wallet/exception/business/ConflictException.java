package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
  public ConflictException(String msg) {
    super(ErrorCode.CONFLICT, msg, HttpStatus.CONFLICT);
  }
}
