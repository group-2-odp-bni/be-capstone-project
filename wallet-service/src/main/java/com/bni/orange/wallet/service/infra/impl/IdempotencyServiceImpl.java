package com.bni.orange.wallet.service.infra.impl;

import com.bni.orange.wallet.exception.business.IdempotencyKeyConflictException;
import com.bni.orange.wallet.model.entity.infra.Idempotency;
import com.bni.orange.wallet.model.enums.IdemStatus;
import com.bni.orange.wallet.repository.infra.IdempotencyRepository;
import com.bni.orange.wallet.service.infra.IdempotencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class IdempotencyServiceImpl implements IdempotencyService {

  private final IdempotencyRepository repo;

  public IdempotencyServiceImpl(IdempotencyRepository repo) {
    this.repo = repo;
  }

  @Override
  @Transactional
  public Optional<String> begin(String scope, String key, String requestHash) {
    var existed = repo.findByScopeAndIdemKey(scope, key);
    if (existed.isEmpty()) {
      var idem = Idempotency.builder()
          .scope(scope)
          .idemKey(key)
          .requestHash(requestHash)
          .status(IdemStatus.PROCESSING)
          .createdAt(OffsetDateTime.now())
          .expiresAt(OffsetDateTime.now().plusHours(72))
          .build();
      repo.save(idem);
      return Optional.empty();
    }

    var idem = existed.get();
    if (!idem.getRequestHash().equals(requestHash)) {
      throw new IdempotencyKeyConflictException("Idempotency-Key reused with different payload");
    }

    return switch (idem.getStatus()) {
      case COMPLETED -> Optional.ofNullable(idem.getResponseBody()).map(Object::toString);
      case PROCESSING -> throw new IdempotencyKeyConflictException("Request with the same Idempotency-Key is still processing");
      case FAILED -> {
        idem.setStatus(IdemStatus.PROCESSING);
        idem.setCreatedAt(OffsetDateTime.now());
        repo.save(idem);
        yield Optional.empty();
      }
    };
  }
@Override
@Transactional
public void complete(String scope, String key, int httpStatus, String responseJson) {
    var idem = repo.findByScopeAndIdemKey(scope, key)
        .orElseThrow(() -> new IllegalStateException("Idempotency record not found"));
    idem.setResponseStatus(httpStatus);
    idem.setResponseBody(responseJson);
    idem.setStatus(IdemStatus.COMPLETED);
    idem.setCompletedAt(OffsetDateTime.now());
    repo.save(idem);
}

  @Override
  @Transactional
  public void fail(String scope, String key) {
    repo.findByScopeAndIdemKey(scope, key).ifPresent(idem -> {
      idem.setStatus(IdemStatus.FAILED);
      repo.save(idem);
    });
  }
}
