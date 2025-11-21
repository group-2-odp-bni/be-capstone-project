package com.bni.orange.wallet.service.query.impl;

import com.bni.orange.wallet.exception.business.ResourceNotFoundException;
import com.bni.orange.wallet.model.entity.WalletTypePolicy;
import com.bni.orange.wallet.model.entity.read.WalletRead;
import com.bni.orange.wallet.model.enums.WalletType;
import com.bni.orange.wallet.model.response.policy.WalletPolicyResponse;
import com.bni.orange.wallet.repository.WalletTypePolicyRepository;
import com.bni.orange.wallet.repository.read.WalletReadRepository;
import com.bni.orange.wallet.service.query.WalletPolicyQueryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletPolicyQueryServiceImpl implements WalletPolicyQueryService {

  private final WalletReadRepository walletReadRepo;
  private final WalletTypePolicyRepository policyRepo;
  private final ObjectMapper objectMapper;

  @Override
  @Transactional(readOnly = true)
  public WalletPolicyResponse getWalletPolicy(UUID walletId) {
    WalletRead wallet = walletReadRepo.findById(walletId)
        .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

    return mapToResponse(loadPolicyOrThrow(wallet.getType()));
  }

  @Override
  @Transactional(readOnly = true)
  public WalletPolicyResponse getPolicyByType(WalletType type) {
    return mapToResponse(loadPolicyOrThrow(type));
  }


  private WalletTypePolicy loadPolicyOrThrow(WalletType type) {
    return policyRepo.findById(type)
        .orElseThrow(() -> new IllegalStateException("Policy not configured for wallet type: " + type));
  }

  private WalletPolicyResponse mapToResponse(WalletTypePolicy p) {
    List<String> roles = parseRoles(p.getAllowMemberDebitRoles());
    return WalletPolicyResponse.builder()
        .walletType(p.getType().name())
        .maxMembers(p.getMaxMembers())
        .defaultDailyCap(p.getDefaultDailyCap())
        .defaultMonthlyCap(p.getDefaultMonthlyCap())
        .allowExternalCredit(p.isAllowExternalCredit())
        .allowMemberDebitRoles(roles)
        .build();
  }

  private List<String> parseRoles(String json) {
    if (json == null || json.isBlank()) return Collections.emptyList();
    try {
      return objectMapper.readValue(json, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      return Collections.emptyList();
    }
  }
}
