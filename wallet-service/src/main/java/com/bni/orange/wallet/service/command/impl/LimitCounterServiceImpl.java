package com.bni.orange.wallet.service.command.impl;

import com.bni.orange.wallet.model.enums.PeriodType;
import com.bni.orange.wallet.repository.UserLimitCounterRepository;
import com.bni.orange.wallet.service.command.LimitCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LimitCounterServiceImpl implements LimitCounterService {

    private final UserLimitCounterRepository repo;

    @Override
    public long getUsed(UUID userId, PeriodType period, OffsetDateTime start) {
        return repo.findUsed(userId, period, start);
    }

    @Override
    public void addUsage(UUID userId, PeriodType period, OffsetDateTime start, long delta) {
        repo.addUsage(userId, period.name(), start, delta);
    }
}
