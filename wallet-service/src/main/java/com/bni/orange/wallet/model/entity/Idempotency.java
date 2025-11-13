package com.bni.orange.wallet.model.entity;

import com.bni.orange.wallet.model.enums.IdemStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "idempotency", schema = "infra",
       uniqueConstraints = @UniqueConstraint(name = "uq_scope_key", columnNames = {"scope","idem_key"}))
public class Idempotency {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)  private String scope;
    @Column(name = "idem_key", nullable = false, length = 128) private String idemKey;
    @Column(name = "request_hash", nullable = false, length = 64) private String requestHash;

    @Column(name = "response_status") private Integer responseStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_body", columnDefinition = "jsonb")
    private JsonNode responseBody;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", nullable = false, columnDefinition = "infra.idem_status")
    private IdemStatus status;

    @Column(name = "created_at", nullable = false)   private OffsetDateTime createdAt;
    @Column(name = "completed_at")                   private OffsetDateTime completedAt;
    @Column(name = "expires_at", nullable = false)   private OffsetDateTime expiresAt;

    public Long getId(){ return id; }
    public String getScope(){ return scope; }
    public void setScope(String s){ scope=s; }
    public String getIdemKey(){ return idemKey; }
    public void setIdemKey(String k){ idemKey=k; }
    public String getRequestHash(){ return requestHash; }
    public void setRequestHash(String h){ requestHash=h; }
    public Integer getResponseStatus(){ return responseStatus; }
    public void setResponseStatus(Integer s){ responseStatus=s; }

    public JsonNode getResponseBody(){ return responseBody; }
    public void setResponseBody(JsonNode b){ responseBody=b; }

    public IdemStatus getStatus(){ return status; }
    public void setStatus(IdemStatus s){ status=s; }

    public OffsetDateTime getCreatedAt(){ return createdAt; }
    public void setCreatedAt(OffsetDateTime t){ createdAt=t; }
    public OffsetDateTime getCompletedAt(){ return completedAt; }
    public void setCompletedAt(OffsetDateTime t){ completedAt=t; }
    public OffsetDateTime getExpiresAt(){ return expiresAt; }
    public void setExpiresAt(OffsetDateTime t){ expiresAt=t; }
}
