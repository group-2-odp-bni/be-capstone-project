package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends ApiException {
  public ForbiddenOperationException(String msg) {
    super(ErrorCode.FORBIDDEN, msg, HttpStatus.FORBIDDEN);
  }
}
