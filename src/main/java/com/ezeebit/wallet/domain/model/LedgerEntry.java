package com.ezeebit.wallet.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An immutable, append-only line in the ledger. The ledger — not the account row —
 * is the source of truth for a balance; {@code balanceAfter} records the running
 * balance so any historical balance can be explained without replaying everything.
 */
public final class LedgerEntry {

    private final Long id;               // null until persisted
    private final long accountId;
    private final long merchantId;
    private final LedgerEntryType type;
    private final Money amount;          // signed: sign matches type.signum()
    private final UUID operationId;      // groups the legs of one business operation
    private final Money balanceAfter;
    private final String reference;      // optional external reference
    private final Instant createdAt;

    public LedgerEntry(Long id, long accountId, long merchantId, LedgerEntryType type,
                       Money amount, UUID operationId, Money balanceAfter,
                       String reference, Instant createdAt) {
        this.id = id;
        this.accountId = accountId;
        this.merchantId = merchantId;
        this.type = Objects.requireNonNull(type, "type");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.operationId = Objects.requireNonNull(operationId, "operationId");
        this.balanceAfter = Objects.requireNonNull(balanceAfter, "balanceAfter");
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public Long id() { return id; }
    public long accountId() { return accountId; }
    public long merchantId() { return merchantId; }
    public LedgerEntryType type() { return type; }
    public Money amount() { return amount; }
    public UUID operationId() { return operationId; }
    public Money balanceAfter() { return balanceAfter; }
    public String reference() { return reference; }
    public Instant createdAt() { return createdAt; }
}
