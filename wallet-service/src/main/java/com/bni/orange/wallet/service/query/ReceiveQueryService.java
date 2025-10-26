package com.bni.orange.wallet.service.query;

import com.bni.orange.wallet.model.response.receive.DefaultReceiveResponse;

public interface ReceiveQueryService {
  DefaultReceiveResponse getDefaultReceiveWallet();
}
