package com.bni.orange.notification.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentIntentCreatedEvent {
  private String paymentIntentId;
  private String userId;
  private String billId;      
  private String currency;      // "IDR"
  private Long amountMinor;     // 125000 = Rp 1.250,00 (2 decimals)
  private String createdAt;
  private Map<String, Object> metadata;
}
