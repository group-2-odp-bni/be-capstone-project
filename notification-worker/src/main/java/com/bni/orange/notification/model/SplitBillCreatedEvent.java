package com.bni.orange.notification.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SplitBillCreatedEvent {
    private String billId;
    private String ownerUserId;
    private String ownerPhoneE164;
    private String ownerFullName;

    private String ownerShortLink;
    private List<MemberLink> memberLinks;
    private String createdAt;
}
