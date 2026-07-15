package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.IncomingPaymentConflictException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An on-chain stablecoin payment arriving for a merchant (Task 4).
 *
 * <p>Lifecycle: the blockchain confirmation service notifies us when funds are first seen
 * and again as the payment becomes more final. Those notifications are at-least-once and can
 * arrive out of order, so:
 * <ul>
 *   <li>Confirmations are tracked as a monotonic maximum — a duplicate or regressed count
 *       never un-does progress.</li>
 *   <li>The payment stays {@code PENDING} (visible but unspendable) until it reaches the
 *       confirmation threshold, at which point {@link #confirm} fires exactly once and the
 *       funds are credited to the ledger.</li>
 *   <li>Auto-settle (Task 5) is tracked separately via {@link SettleStatus} so an outbox
 *       redelivery can never convert twice.</li>
 * </ul>
 * Dedupe across the whole flow is by {@code (txHash, outputIndex)}.
 */
public final class IncomingPayment {

    public enum Status { PENDING, CONFIRMED }

    public enum SettleStatus { NONE, REQUESTED, SETTLED, SKIPPED }

    private final UUID id;
    private final long merchantId;
    private final String txHash;
    private final int outputIndex;
    private final Money amount;
    private int confirmations;
    private Status status;
    private UUID creditOperationId;
    private SettleStatus settleStatus;
    private UUID settlementConversionId;
    private final Instant firstSeenAt;
    private Instant confirmedAt;
    private Instant updatedAt;

    public IncomingPayment(UUID id, long merchantId, String txHash, int outputIndex, Money amount,
                           int confirmations, Status status, UUID creditOperationId,
                           SettleStatus settleStatus, UUID settlementConversionId,
                           Instant firstSeenAt, Instant confirmedAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.merchantId = merchantId;
        this.txHash = Objects.requireNonNull(txHash);
        this.outputIndex = outputIndex;
        this.amount = Objects.requireNonNull(amount);
        this.confirmations = confirmations;
        this.status = Objects.requireNonNull(status);
        this.creditOperationId = creditOperationId;
        this.settleStatus = Objects.requireNonNull(settleStatus);
        this.settlementConversionId = settlementConversionId;
        this.firstSeenAt = firstSeenAt;
        this.confirmedAt = confirmedAt;
        this.updatedAt = updatedAt;
    }

    /** First sighting of a payment. Only stablecoins arrive on-chain. */
    public static IncomingPayment observed(long merchantId, String txHash, int outputIndex,
                                           Money amount, int confirmations, Instant now) {
        if (!amount.currency().isStablecoin()) {
            throw new IllegalArgumentException(
                    "incoming payments must be a stablecoin, got " + amount.currency());
        }
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("incoming payment amount must be positive");
        }
        if (outputIndex < 0) {
            throw new IllegalArgumentException("output index must not be negative");
        }
        return new IncomingPayment(UUID.randomUUID(), merchantId, txHash, outputIndex, amount,
                Math.max(0, confirmations), Status.PENDING, null,
                SettleStatus.NONE, null, now, null, now);
    }

    /**
     * Fold in a new confirmation count, keeping the monotonic maximum. Returns whether the
     * count advanced (a duplicate or regressed notification is a no-op).
     */
    public boolean recordConfirmations(int reported, Instant now) {
        if (reported <= confirmations) {
            return false;
        }
        this.confirmations = reported;
        this.updatedAt = now;
        return true;
    }

    /**
     * Promote to CONFIRMED (funds become spendable) if the threshold is met. Single-shot:
     * returns true only on the PENDING -> CONFIRMED transition, so the caller credits the
     * ledger exactly once. Also arms auto-settle by moving to {@code REQUESTED}.
     */
    public boolean confirm(int threshold, UUID creditOperationId, Instant now) {
        if (status != Status.PENDING || confirmations < threshold) {
            return false;
        }
        this.status = Status.CONFIRMED;
        this.creditOperationId = Objects.requireNonNull(creditOperationId);
        this.settleStatus = SettleStatus.REQUESTED;
        this.confirmedAt = now;
        this.updatedAt = now;
        return true;
    }

    /** Guard that a replayed notification matches the immutable facts of the recorded payment. */
    public void assertMatches(long merchantId, Money amount) {
        if (this.merchantId != merchantId
                || this.amount.currency() != amount.currency()
                || this.amount.amount().compareTo(amount.amount()) != 0) {
            throw new IncomingPaymentConflictException(txHash, outputIndex);
        }
    }

    /** Auto-settle completed. Legal only from REQUESTED; a redelivery is a no-op (returns false). */
    public boolean markSettled(UUID conversionId, Instant now) {
        if (settleStatus != SettleStatus.REQUESTED) {
            return false;
        }
        this.settleStatus = SettleStatus.SETTLED;
        this.settlementConversionId = Objects.requireNonNull(conversionId);
        this.updatedAt = now;
        return true;
    }

    /** No rule applied (or a zero portion). Legal only from REQUESTED; a redelivery is a no-op. */
    public boolean markSkipped(Instant now) {
        if (settleStatus != SettleStatus.REQUESTED) {
            return false;
        }
        this.settleStatus = SettleStatus.SKIPPED;
        this.updatedAt = now;
        return true;
    }

    public UUID id() { return id; }
    public long merchantId() { return merchantId; }
    public String txHash() { return txHash; }
    public int outputIndex() { return outputIndex; }
    public Money amount() { return amount; }
    public int confirmations() { return confirmations; }
    public Status status() { return status; }
    public UUID creditOperationId() { return creditOperationId; }
    public SettleStatus settleStatus() { return settleStatus; }
    public UUID settlementConversionId() { return settlementConversionId; }
    public Instant firstSeenAt() { return firstSeenAt; }
    public Instant confirmedAt() { return confirmedAt; }
    public Instant updatedAt() { return updatedAt; }
}
