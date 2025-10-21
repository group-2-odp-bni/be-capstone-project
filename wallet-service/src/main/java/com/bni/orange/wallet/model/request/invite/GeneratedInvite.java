package com.bni.orange.wallet.model.request.invite;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedInvite {
  private String phoneMasked;
  private String link;
  private String code;
}