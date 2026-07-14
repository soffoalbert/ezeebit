package com.ezeebit.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A completed (or failed) conversion of one currency into another, executed
 * against a {@link Quote}. Recorded for the audit trail; the actual balance
 * movements live in the ledger under a shared operation id.
 */
public final class Conversion {

    public enum Status { EXECUTED, FAILED }

    private final UUID id;
    private final long merchantId;
    private final UUID quoteId;
    private final Money fromAmount;
    private final Money toAmount;
    private final BigDecimal rate;
    private final Status status;
    private final UUID operationId;
    private final Instant createdAt;
    private final Instant executedAt;

    public Conversion(UUID id, long merchantId, UUID quoteId, Money fromAmount, Money toAmount,
                      BigDecimal rate, Status status, UUID operationId,
                      Instant createdAt, Instant executedAt) {
        this.id = Objects.requireNonNull(id);
        this.merchantId = merchantId;
        this.quoteId = Objects.requireNonNull(quoteId);
        this.fromAmount = Objects.requireNonNull(fromAmount);
        this.toAmount = Objects.requireNonNull(toAmount);
        this.rate = Objects.requireNonNull(rate);
        this.status = Objects.requireNonNull(status);
        this.operationId = Objects.requireNonNull(operationId);
        this.createdAt = createdAt;
        this.executedAt = executedAt;
    }

    public static Conversion executed(long merchantId, Quote quote, UUID operationId, Instant now) {
        return new Conversion(UUID.randomUUID(), merchantId, quote.id(),
                quote.fromAmount(), quote.toAmount(), quote.rate(),
                Status.EXECUTED, operationId, now, now);
    }

    public UUID id() { return id; }
    public long merchantId() { return merchantId; }
    public UUID quoteId() { return quoteId; }
    public Money fromAmount() { return fromAmount; }
    public Money toAmount() { return toAmount; }
    public BigDecimal rate() { return rate; }
    public Status status() { return status; }
    public UUID operationId() { return operationId; }
    public Instant createdAt() { return createdAt; }
    public Instant executedAt() { return executedAt; }
}
