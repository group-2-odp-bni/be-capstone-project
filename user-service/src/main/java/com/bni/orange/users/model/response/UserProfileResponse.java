package com.bni.orange.users.model.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private UUID id;
    private String name;
    private String email;
    private String phoneNumber;
    private String bio;
    private String address;
    private LocalDate dateOfBirth;
    private String profileImageUrl;
    private Boolean emailVerified;
    private Boolean phoneVerified;
    private OffsetDateTime emailVerifiedAt;
    private OffsetDateTime phoneVerifiedAt;
}
