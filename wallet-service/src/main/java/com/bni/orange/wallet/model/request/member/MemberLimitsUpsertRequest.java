package com.bni.orange.wallet.model.request.member;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MemberLimitsUpsertRequest {

  @PositiveOrZero
  @Digits(integer = 20, fraction = 2)
  private BigDecimal perTransaction;

  @PositiveOrZero
  @Digits(integer = 20, fraction = 2)
  private BigDecimal daily;

  @PositiveOrZero
  @Digits(integer = 20, fraction = 2)
  private BigDecimal weekly;

  @PositiveOrZero
  @Digits(integer = 20, fraction = 2)
  private BigDecimal monthly;

  private String currency;
}
