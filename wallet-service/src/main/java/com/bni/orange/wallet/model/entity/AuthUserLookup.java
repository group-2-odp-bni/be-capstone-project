package com.bni.orange.wallet.model.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(schema = "auth_read", name = "user_lookup")
public class AuthUserLookup {
    @Id
    private UUID id;

    @Column(name = "phone_number", nullable = false)
    private String phoneNumber;

    @Column(name = "name")   private String name;
    @Column(name = "status") private String status;
    @Column(name = "last_login_at") private OffsetDateTime lastLoginAt;
    @Column(name = "updated_at")    private OffsetDateTime updatedAt;

    public UUID getId(){ return id; } public void setId(UUID id){ this.id=id; }
    public String getPhoneNumber(){ return phoneNumber; } public void setPhoneNumber(String p){ this.phoneNumber=p; }
    public String getName(){ return name; } public void setName(String n){ this.name=n; }
    public String getStatus(){ return status; } public void setStatus(String s){ this.status=s; }
    public OffsetDateTime getLastLoginAt(){ return lastLoginAt; } public void setLastLoginAt(OffsetDateTime t){ this.lastLoginAt=t; }
    public OffsetDateTime getUpdatedAt(){ return updatedAt; } public void setUpdatedAt(OffsetDateTime t){ this.updatedAt=t; }
}
