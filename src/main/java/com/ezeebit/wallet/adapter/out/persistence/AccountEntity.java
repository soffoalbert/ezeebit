package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "account")
class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal balance;

    @Version
    private Long version;

    // updatable=false: the domain Account does not carry createdAt, so it must never be
    // written on the merge-based update path (it would overwrite the row with null).
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() {}

    static AccountEntity fromDomain(Account account) {
        AccountEntity e = new AccountEntity();
        e.id = account.id();
        e.merchantId = account.merchantId();
        e.currency = account.currency();
        e.balance = account.balance().amount();
        e.version = account.version() == 0 && account.id() == null ? null : account.version();
        return e;
    }

    Account toDomain() {
        return new Account(id, merchantId, currency,
                Money.of(balance, currency), version == null ? 0 : version);
    }

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    Long getId() {
        return id;
    }
}
