package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.Withdrawal;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "withdrawal")
class WithdrawalEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json")
    private String destination;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Withdrawal.Status status;

    @Column(name = "payout_reference", length = 120)
    private String payoutReference;

    @Column(name = "failure_reason", length = 255)
    private String failureReason;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WithdrawalEntity() {}

    static WithdrawalEntity fromDomain(Withdrawal w) {
        WithdrawalEntity e = new WithdrawalEntity();
        e.id = w.id().toString();
        e.merchantId = w.merchantId();
        e.idempotencyKey = w.idempotencyKey();
        e.currency = w.amount().currency();
        e.amount = w.amount().amount();
        e.destination = w.destination();
        e.status = w.status();
        e.payoutReference = w.payoutReference();
        e.failureReason = w.failureReason();
        e.operationId = w.operationId().toString();
        e.createdAt = w.createdAt();
        e.updatedAt = w.updatedAt();
        return e;
    }

    Withdrawal toDomain() {
        return new Withdrawal(UUID.fromString(id), merchantId, idempotencyKey,
                Money.of(amount, currency), destination, status, payoutReference,
                failureReason, UUID.fromString(operationId), createdAt, updatedAt);
    }
}
