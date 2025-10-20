package com.bni.orange.wallet.service.infra;

import java.util.Optional;

public interface IdempotencyService {

  Optional<String> begin(String scope, String key, String requestHash);
  void complete(String scope, String key, int httpStatus, String responseJson);
  void fail(String scope, String key);
}
