package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.IllegalWithdrawalStateException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A payout of a merchant's funds to an external destination.
 *
 * <p>State machine: {@code PENDING -> SUBMITTED -> COMPLETED | FAILED}. Funds are
 * held (debited) at creation, before the payout rail is ever called, so concurrent
 * or duplicate requests can never double-spend. Terminal transitions are guarded so
 * that duplicate rail callbacks are harmless no-ops.
 */
public final class Withdrawal {

    public enum Status { PENDING, SUBMITTED, COMPLETED, FAILED }

    private final UUID id;
    private final long merchantId;
    private final String idempotencyKey;
    private final Money amount;
    private final String destination;   // opaque JSON (bank details / chain address)
    private Status status;
    private String payoutReference;
    private String partnerCode;         // the routed partner, set at submission (Task 6)
    private String failureReason;
    private final UUID operationId;
    private final Instant createdAt;
    private Instant updatedAt;

    public Withdrawal(UUID id, long merchantId, String idempotencyKey, Money amount,
                      String destination, Status status, String payoutReference, String partnerCode,
                      String failureReason, UUID operationId, Instant createdAt, Instant updatedAt) {
        this.id = Objects.requireNonNull(id);
        this.merchantId = merchantId;
        this.idempotencyKey = idempotencyKey;
        this.amount = Objects.requireNonNull(amount);
        this.destination = Objects.requireNonNull(destination);
        this.status = Objects.requireNonNull(status);
        this.payoutReference = payoutReference;
        this.partnerCode = partnerCode;
        this.failureReason = failureReason;
        this.operationId = Objects.requireNonNull(operationId);
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static Withdrawal requested(long merchantId, String idempotencyKey, Money amount,
                                       String destination, Instant now) {
        return new Withdrawal(UUID.randomUUID(), merchantId, idempotencyKey, amount, destination,
                Status.PENDING, null, null, null, UUID.randomUUID(), now, now);
    }

    /** The funds are held and the chosen partner accepted the request. */
    public void markSubmitted(String payoutReference, String partnerCode, Instant now) {
        requireStatus(Status.PENDING);
        this.status = Status.SUBMITTED;
        this.payoutReference = payoutReference;
        this.partnerCode = partnerCode;
        this.updatedAt = now;
    }

    /** Rail reported success. Idempotent: a second success callback is ignored. */
    public boolean markCompleted(Instant now) {
        if (status == Status.COMPLETED) {
            return false;
        }
        if (status != Status.SUBMITTED) {
            throw new IllegalWithdrawalStateException(id, status, Status.COMPLETED);
        }
        this.status = Status.COMPLETED;
        this.updatedAt = now;
        return true;
    }

    /** Rail reported failure. Idempotent: a second failure callback is ignored. */
    public boolean markFailed(String reason, Instant now) {
        if (status == Status.FAILED) {
            return false;
        }
        if (status != Status.SUBMITTED && status != Status.PENDING) {
            throw new IllegalWithdrawalStateException(id, status, Status.FAILED);
        }
        this.status = Status.FAILED;
        this.failureReason = reason;
        this.updatedAt = now;
        return true;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalWithdrawalStateException(id, status, expected);
        }
    }

    public UUID id() { return id; }
    public long merchantId() { return merchantId; }
    public String idempotencyKey() { return idempotencyKey; }
    public Money amount() { return amount; }
    public String destination() { return destination; }
    public Status status() { return status; }
    public String payoutReference() { return payoutReference; }
    public String partnerCode() { return partnerCode; }
    public String failureReason() { return failureReason; }
    public UUID operationId() { return operationId; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
