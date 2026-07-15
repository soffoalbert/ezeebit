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
                               Withdrawal withdrawal, Incoming incoming) {

    public record Conversion(int quoteTtlSeconds, BigDecimal spread, BigDecimal maxRateDeviation) {}

    /** Number of on-chain confirmations before an incoming payment becomes spendable (Task 4). */
    public record Incoming(int confirmationThreshold) {}

    public record ExchangeRate(long latencyMs, double failureRate) {}

    /**
     * Payout knobs. {@code settlementDelayMs}/{@code failureRate} are the global stub defaults;
     * {@code partners} overrides them per partner code (any unset field falls back to the global).
     * {@code pendingDeadlineMs} is how long a held-but-unsubmitted payout may stay PENDING before
     * the sweeper fails it and releases the funds (money is never stuck).
     */
    public record Payout(long settlementDelayMs, double failureRate, long pendingDeadlineMs,
                         Map<String, PartnerStub> partners) {
        public Payout {
            partners = partners == null ? Map.of() : partners;
        }
    }

    /** Per-partner stub overrides; null fields fall back to the global payout knobs. */
    public record PartnerStub(Double failureRate, Boolean unavailable, Long settlementDelayMs) {}

    /** Default per-currency single-withdrawal cap, applied when no per-merchant override exists. */
    public record Withdrawal(Map<String, BigDecimal> maxPerCurrency) {
        public Withdrawal {
            maxPerCurrency = maxPerCurrency == null ? Map.of() : maxPerCurrency;
        }
    }
}
