package com.bni.orange.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentStatusUpdatedEvent {
  private String paymentIntentId;
  private String status;        // "PENDING","SUCCEEDED","FAILED","CANCELLED"
  private String updatedAt;
  private String failureCode;     // optional
  private String failureMessage;  // optional
  private Map<String, Object> metadata;
}
