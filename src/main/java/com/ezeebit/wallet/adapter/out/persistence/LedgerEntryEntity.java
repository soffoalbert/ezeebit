package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.LedgerEntry;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_entry")
class LedgerEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private LedgerEntryType type;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "balance_after", nullable = false, precision = 38, scale = 18)
    private BigDecimal balanceAfter;

    @Column(length = 255)
    private String reference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerEntryEntity() {}

    static LedgerEntryEntity fromDomain(LedgerEntry e) {
        LedgerEntryEntity entity = new LedgerEntryEntity();
        entity.accountId = e.accountId();
        entity.merchantId = e.merchantId();
        entity.currency = e.amount().currency();
        entity.amount = e.amount().amount();
        entity.type = e.type();
        entity.operationId = e.operationId().toString();
        entity.balanceAfter = e.balanceAfter().amount();
        entity.reference = e.reference();
        return entity;
    }

    LedgerEntry toDomain() {
        return new LedgerEntry(id, accountId, merchantId, type,
                Money.of(amount, currency), UUID.fromString(operationId),
                Money.of(balanceAfter, currency), reference, createdAt);
    }

    @PrePersist
    void onInsert() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
