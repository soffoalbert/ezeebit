package com.ezeebit.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Tunable knobs, bound from the {@code wallet.*} configuration. The exchange-rate and
 * payout sections drive the stubbed external services so failure handling (latency,
 * failures, bad rates) can be exercised without real dependencies.
 */
@ConfigurationProperties(prefix = "wallet")
public record WalletProperties(Conversion conversion, ExchangeRate exchangeRate, Payout payout,
                               Withdrawal withdrawal) {

    public record Conversion(int quoteTtlSeconds, BigDecimal spread, BigDecimal maxRateDeviation) {}

    public record ExchangeRate(long latencyMs, double failureRate) {}

    public record Payout(long settlementDelayMs, double failureRate) {}

    /** Default per-currency single-withdrawal cap, applied when no per-merchant override exists. */
    public record Withdrawal(Map<String, BigDecimal> maxPerCurrency) {
        public Withdrawal {
            maxPerCurrency = maxPerCurrency == null ? Map.of() : maxPerCurrency;
        }
    }
}
