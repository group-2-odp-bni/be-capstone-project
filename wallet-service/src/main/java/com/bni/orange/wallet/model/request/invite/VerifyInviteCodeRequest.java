package com.bni.orange.wallet.model.request.invite;

import lombok.Data;
@Data
public class VerifyInviteCodeRequest {
  private String token; 
  private String code; 
}
