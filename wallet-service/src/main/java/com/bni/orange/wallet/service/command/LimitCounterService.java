package com.bni.orange.wallet.service.command;

import com.bni.orange.wallet.model.enums.PeriodType;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface LimitCounterService {
    long getUsed(UUID userId, PeriodType period, OffsetDateTime start);

    void addUsage(UUID userId, PeriodType period, OffsetDateTime start, long delta);
}
