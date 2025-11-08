package com.bni.orange.notification.model;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public  class MemberLink {
  private String userId;
  private String memberId;
  private String shortLink;
  private String phoneE164;

}