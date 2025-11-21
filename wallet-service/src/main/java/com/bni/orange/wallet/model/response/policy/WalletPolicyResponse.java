package com.bni.orange.wallet.model.response.policy;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletPolicyResponse {
  private String walletType;
  private int maxMembers;
  private long defaultDailyCap;
  private long defaultMonthlyCap;
  private boolean allowExternalCredit;

  private List<String> allowMemberDebitRoles;
}
