package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.QuoteAlreadyUsedException;
import com.ezeebit.wallet.domain.exception.QuoteExpiredException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A price the merchant was quoted for a conversion, locked for a short window.
 * The merchant executes against this exact rate, so they never suffer surprise
 * slippage; the TTL bounds how long the platform is exposed to the locked price.
 */
public final class Quote {

    public enum Status { ACTIVE, USED, EXPIRED }

    private final UUID id;
    private final long merchantId;
    private final Money fromAmount;
    private final Money toAmount;
    private final java.math.BigDecimal rate;      // effective, incl. spread: toAmount = fromAmount * rate
    private final java.math.BigDecimal midRate;   // raw feed mid-rate, kept for reconciliation
    private Status status;
    private final Instant createdAt;
    private final Instant expiresAt;

    public Quote(UUID id, long merchantId, Money fromAmount, Money toAmount,
                 java.math.BigDecimal rate, java.math.BigDecimal midRate, Status status,
                 Instant createdAt, Instant expiresAt) {
        this.id = Objects.requireNonNull(id);
        this.merchantId = merchantId;
        this.fromAmount = Objects.requireNonNull(fromAmount);
        this.toAmount = Objects.requireNonNull(toAmount);
        this.rate = Objects.requireNonNull(rate);
        this.midRate = midRate;
        this.status = Objects.requireNonNull(status);
        this.createdAt = createdAt;
        this.expiresAt = Objects.requireNonNull(expiresAt);
    }

    /** Assert this quote may be executed right now, then mark it used. */
    public void consume(Instant now) {
        if (status == Status.USED) {
            throw new QuoteAlreadyUsedException(id);
        }
        if (status == Status.EXPIRED || now.isAfter(expiresAt)) {
            this.status = Status.EXPIRED;
            throw new QuoteExpiredException(id, expiresAt);
        }
        this.status = Status.USED;
    }

    public boolean isExpiredAt(Instant now) {
        return now.isAfter(expiresAt);
    }

    public UUID id() { return id; }
    public long merchantId() { return merchantId; }
    public Money fromAmount() { return fromAmount; }
    public Money toAmount() { return toAmount; }
    public java.math.BigDecimal rate() { return rate; }
    public java.math.BigDecimal midRate() { return midRate; }
    public Status status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant expiresAt() { return expiresAt; }
}
