package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {
  public ResourceNotFoundException(String msg) {
    super(ErrorCode.NOT_FOUND, msg, HttpStatus.NOT_FOUND);
  }
}
