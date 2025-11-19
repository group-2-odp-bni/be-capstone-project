package com.bni.orange.wallet.model.response.member;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberLimitsResponse {
  private BigDecimal perTransaction;
  private BigDecimal daily;
  private BigDecimal weekly;
  private BigDecimal monthly;
  private String currency;
}
