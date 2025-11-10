package com.bni.orange.notification.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitBillRemindedEvent {
  private String billId;
  private String remindedByUserId;
  private List<String> requestedChannels;
  private List<MemberLink> memberLinks;
  private Map<String, Object> result;     
}
