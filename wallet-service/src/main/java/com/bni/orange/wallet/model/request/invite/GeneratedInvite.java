package com.bni.orange.wallet.model.request.invite;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GeneratedInvite {
  private String phoneMasked;
  private String link;
  private String code;
}