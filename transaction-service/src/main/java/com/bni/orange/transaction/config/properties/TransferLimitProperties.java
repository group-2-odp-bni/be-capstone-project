package com.bni.orange.transaction.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

@ConfigurationProperties(prefix = "orange.transaction.limit.transfer")
public record TransferLimitProperties(
    BigDecimal minAmount,
    BigDecimal maxAmount
) {}
