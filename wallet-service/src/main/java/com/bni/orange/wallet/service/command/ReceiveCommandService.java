package com.bni.orange.wallet.service.command;

import com.bni.orange.wallet.model.request.receive.SetDefaultReceiveRequest;
import com.bni.orange.wallet.model.response.receive.DefaultReceiveResponse;

public interface ReceiveCommandService {
  DefaultReceiveResponse setDefaultReceiveWallet(SetDefaultReceiveRequest req);
}
