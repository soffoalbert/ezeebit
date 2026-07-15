package com.ezeebit.wallet.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A completed (or failed) conversion of one currency into another. Recorded for the
 * audit trail; the actual balance movements live in the ledger under a shared operation id.
 *
 * <p>Two triggers exist: a {@link TriggerType#QUOTED} conversion executes a merchant's
 * quote (Task 2); an {@link TriggerType#AUTO_SETTLE} conversion is driven by an auto-settle
 * rule at market rate when an incoming payment lands (Task 5) — it has no quote, but links
 * back to the incoming payment that caused it.
 */
public final class Conversion {

    public enum Status { EXECUTED, FAILED }

    public enum TriggerType { QUOTED, AUTO_SETTLE }

    private final UUID id;
    private final long merchantId;
    private final UUID quoteId;                 // null for AUTO_SETTLE
    private final TriggerType triggerType;
    private final UUID incomingPaymentId;       // set for AUTO_SETTLE
    private final Money fromAmount;
    private final Money toAmount;
    private final BigDecimal rate;
    private final Status status;
    private final UUID operationId;
    private final Instant createdAt;
    private final Instant executedAt;

    public Conversion(UUID id, long merchantId, UUID quoteId, TriggerType triggerType,
                      UUID incomingPaymentId, Money fromAmount, Money toAmount, BigDecimal rate,
                      Status status, UUID operationId, Instant createdAt, Instant executedAt) {
        this.id = Objects.requireNonNull(id);
        this.merchantId = merchantId;
        this.quoteId = quoteId;   // nullable: an auto-settle conversion has no quote
        this.triggerType = Objects.requireNonNull(triggerType);
        this.incomingPaymentId = incomingPaymentId;
        this.fromAmount = Objects.requireNonNull(fromAmount);
        this.toAmount = Objects.requireNonNull(toAmount);
        this.rate = Objects.requireNonNull(rate);
        this.status = Objects.requireNonNull(status);
        this.operationId = Objects.requireNonNull(operationId);
        this.createdAt = createdAt;
        this.executedAt = executedAt;
    }

    public static Conversion executed(long merchantId, Quote quote, UUID operationId, Instant now) {
        return new Conversion(UUID.randomUUID(), merchantId, quote.id(), TriggerType.QUOTED, null,
                quote.fromAmount(), quote.toAmount(), quote.rate(),
                Status.EXECUTED, operationId, now, now);
    }

    /** A market-rate conversion driven by an auto-settle rule (no quote). */
    public static Conversion autoSettled(long merchantId, UUID incomingPaymentId, Money fromAmount,
                                         Money toAmount, BigDecimal rate, UUID operationId, Instant now) {
        return new Conversion(UUID.randomUUID(), merchantId, null, TriggerType.AUTO_SETTLE,
                Objects.requireNonNull(incomingPaymentId), fromAmount, toAmount, rate,
                Status.EXECUTED, operationId, now, now);
    }

    public UUID id() { return id; }
    public long merchantId() { return merchantId; }
    public UUID quoteId() { return quoteId; }
    public TriggerType triggerType() { return triggerType; }
    public UUID incomingPaymentId() { return incomingPaymentId; }
    public Money fromAmount() { return fromAmount; }
    public Money toAmount() { return toAmount; }
    public BigDecimal rate() { return rate; }
    public Status status() { return status; }
    public UUID operationId() { return operationId; }
    public Instant createdAt() { return createdAt; }
    public Instant executedAt() { return executedAt; }
}
