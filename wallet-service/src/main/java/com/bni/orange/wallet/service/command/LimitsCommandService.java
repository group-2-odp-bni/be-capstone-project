package com.bni.orange.wallet.service.command;

import com.bni.orange.wallet.model.request.limits.UserLimitsUpdateRequest;
import com.bni.orange.wallet.model.response.limits.UserLimitsResponse;

public interface LimitsCommandService {
  UserLimitsResponse updateMyLimits(UserLimitsUpdateRequest req);
}
