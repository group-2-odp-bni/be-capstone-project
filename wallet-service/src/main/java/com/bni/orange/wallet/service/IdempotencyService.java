package com.bni.orange.wallet.service;

import com.bni.orange.wallet.exception.IdempotencyKeyConflictException;
import com.bni.orange.wallet.exception.RequestInProcessException;
import com.bni.orange.wallet.model.entity.Idempotency;
import com.bni.orange.wallet.model.enums.IdemStatus;
import com.bni.orange.wallet.model.response.ApiResponse;
import com.bni.orange.wallet.repository.IdempotencyRepository;
import com.bni.orange.wallet.utils.HashUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class IdempotencyService {
    private final IdempotencyRepository repo;
    private final ObjectMapper om;

    public IdempotencyService(IdempotencyRepository repo, ObjectMapper om) {
        this.repo = repo; this.om = om;
    }

    public record IdemResult(boolean fresh, Integer replayStatus, ApiResponse<?> replayBody){
        public static IdemResult freshResult(){ return new IdemResult(true, null, null); }
        public static IdemResult replayedResult(Integer s, ApiResponse<?> b){ return new IdemResult(false, s, b); }
    }

    @Transactional
    public IdemResult beginOrReplay(String scope, String idemKey, Object requestBody) {
        if (idemKey == null || idemKey.isBlank())
            throw new IllegalArgumentException("Idempotency-Key is required");

        var now = OffsetDateTime.now(ZoneOffset.UTC);

        var hash = HashUtil.sha256(HashUtil.canonicalJson(om, requestBody));
        var existing = repo.findByScopeAndIdemKey(scope, idemKey).orElse(null);

        if (existing != null) {
            if (!existing.getRequestHash().equals(hash))
                throw new IdempotencyKeyConflictException("IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST");

            var st = existing.getStatus();
            if (st == IdemStatus.COMPLETED) {
                try {
                    ApiResponse<?> resp = om.treeToValue(existing.getResponseBody(), ApiResponse.class);
                    return IdemResult.replayedResult(existing.getResponseStatus(), resp);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            } else if (st == IdemStatus.PROCESSING) {
                throw new RequestInProcessException("REQUEST_IN_PROCESS");
            } else if (st == IdemStatus.FAILED) {
                throw new RequestInProcessException("PREVIOUS_REQUEST_FAILED_RETRY_LATER");
            } else {
                throw new RequestInProcessException("UNKNOWN_IDEMPOTENCY_STATE");
            }
        }

        var row = new Idempotency();
        row.setScope(scope);
        row.setIdemKey(idemKey);
        row.setRequestHash(hash);
        row.setStatus(IdemStatus.PROCESSING);
        row.setCreatedAt(now);
        row.setExpiresAt(now.plusHours(72));
        row.setResponseStatus(null);
        row.setResponseBody(null);
        repo.saveAndFlush(row);

        return IdemResult.freshResult();
    }

    @Transactional
    public void complete(String scope, String idemKey, int httpStatus, ApiResponse<?> body){
        var row = repo.findByScopeAndIdemKey(scope, idemKey).orElseThrow();
        try {
            row.setResponseStatus(httpStatus);
            JsonNode json = om.valueToTree(body);
            row.setResponseBody(json);
            row.setStatus(IdemStatus.COMPLETED);
            row.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
            repo.save(row);
        } catch (Exception e){
            row.setStatus(IdemStatus.FAILED);
            repo.save(row);
            throw new RuntimeException(e);
        }
    }
}
