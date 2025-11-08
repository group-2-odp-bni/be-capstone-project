package com.bni.orange.wallet.exception.business;

import com.bni.orange.wallet.exception.ApiException;
import com.bni.orange.wallet.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public class MaxMemberReachException extends ApiException {
  public MaxMemberReachException(String msg) {
    super(ErrorCode.MAX_MEMBERS_REACHED, msg, HttpStatus.NOT_ACCEPTABLE);
  }    
}
