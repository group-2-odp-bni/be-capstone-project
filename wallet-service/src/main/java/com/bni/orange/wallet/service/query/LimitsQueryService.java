package com.bni.orange.wallet.service.query;

import com.bni.orange.wallet.model.response.limits.UserLimitsResponse;


public interface LimitsQueryService {
  UserLimitsResponse getMyLimits();
}
