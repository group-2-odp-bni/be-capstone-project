package com.bni.orange.wallet.model.response.invite;
import lombok.Builder;
import lombok.Value;
import java.time.OffsetDateTime;
import java.util.UUID;

@Value @Builder
public class VerifyInviteCodeResponse {
  String status;            // "VERIFIED" | "INVALID_CODE" | "EXPIRED"
  UUID   walletId;
  String phoneMasked;       
  boolean verified;         
  OffsetDateTime expiresAt; 
  String boundToken;       
}