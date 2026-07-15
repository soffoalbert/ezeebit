package com.ezeebit.wallet.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

/**
 * A merchant's standing instruction to auto-convert a percentage of an incoming stablecoin
 * into their local fiat currency (Task 5). One rule per (merchant, source currency).
 *
 * <p>Invariants enforced here: the source is a stablecoin, the target is a distinct fiat
 * currency, and the percentage is in {@code (0, 100]}.
 */
public final class AutoSettleRule {

    private final Long id;                 // null until persisted
    private final long merchantId;
    private final Currency sourceCurrency;
    private final Currency targetCurrency;
    private final BigDecimal percentage;   // percent, e.g. 50.0 = 50%
    private final boolean enabled;
    private final Instant createdAt;
    private final Instant updatedAt;

    public AutoSettleRule(Long id, long merchantId, Currency sourceCurrency, Currency targetCurrency,
                          BigDecimal percentage, boolean enabled, Instant createdAt, Instant updatedAt) {
        this.sourceCurrency = Objects.requireNonNull(sourceCurrency, "sourceCurrency");
        this.targetCurrency = Objects.requireNonNull(targetCurrency, "targetCurrency");
        this.percentage = Objects.requireNonNull(percentage, "percentage");
        if (!sourceCurrency.isStablecoin()) {
            throw new IllegalArgumentException("auto-settle source must be a stablecoin, got " + sourceCurrency);
        }
        if (!targetCurrency.isFiat()) {
            throw new IllegalArgumentException("auto-settle target must be a fiat currency, got " + targetCurrency);
        }
        if (sourceCurrency == targetCurrency) {
            throw new IllegalArgumentException("auto-settle source and target must differ");
        }
        if (percentage.signum() <= 0 || percentage.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalArgumentException("auto-settle percentage must be in (0, 100], got " + percentage);
        }
        this.id = id;
        this.merchantId = merchantId;
        this.enabled = enabled;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static AutoSettleRule of(long merchantId, Currency source, Currency target,
                                    BigDecimal percentage, boolean enabled, Instant now) {
        return new AutoSettleRule(null, merchantId, source, target, percentage, enabled, now, now);
    }

    /**
     * The portion of an incoming amount to convert, rounded DOWN to the source currency's
     * scale so the platform never over-converts (the remainder stays in the merchant's
     * stablecoin balance). The incoming money must be in this rule's source currency.
     */
    public Money portionOf(Money incoming) {
        if (incoming.currency() != sourceCurrency) {
            throw new IllegalArgumentException(
                    "incoming currency " + incoming.currency() + " does not match rule source " + sourceCurrency);
        }
        BigDecimal raw = incoming.amount()
                .multiply(percentage)
                .divide(BigDecimal.valueOf(100), sourceCurrency.scale(), RoundingMode.DOWN);
        return Money.of(raw, sourceCurrency);
    }

    public Long id() { return id; }
    public long merchantId() { return merchantId; }
    public Currency sourceCurrency() { return sourceCurrency; }
    public Currency targetCurrency() { return targetCurrency; }
    public BigDecimal percentage() { return percentage; }
    public boolean enabled() { return enabled; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
