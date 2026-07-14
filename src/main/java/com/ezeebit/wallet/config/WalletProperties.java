package com.ezeebit.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Tunable knobs, bound from the {@code wallet.*} configuration. The exchange-rate and
 * payout sections drive the stubbed external services so failure handling (latency,
 * failures, bad rates) can be exercised without real dependencies.
 */
@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(Conversion conversion, ExchangeRate exchangeRate, Payout payout) {

    public record Conversion(int quoteTtlSeconds, BigDecimal spread, BigDecimal maxRateDeviation) {}

    public record ExchangeRate(long latencyMs, double failureRate) {}

    public record Payout(long settlementDelayMs, double failureRate) {}
}
