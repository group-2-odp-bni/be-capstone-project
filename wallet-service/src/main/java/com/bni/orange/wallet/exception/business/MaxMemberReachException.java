package com.bni.orange.wallet.exception.business;

import org.springframework.http.HttpStatus;
import com.bni.orange.wallet.exception.ApiException;

import com.bni.orange.wallet.exception.ErrorCode;

public class MaxMemberReachException extends ApiException {
  public MaxMemberReachException(String msg) {
    super(ErrorCode.MAX_MEMBERS_REACHED, msg, HttpStatus.NOT_ACCEPTABLE);
  }    
}
