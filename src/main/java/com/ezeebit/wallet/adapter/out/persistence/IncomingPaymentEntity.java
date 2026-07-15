package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.IncomingPayment;
import com.ezeebit.wallet.domain.model.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "incoming_payment")
class IncomingPaymentEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "tx_hash", nullable = false, length = 80)
    private String txHash;

    @Column(name = "output_index", nullable = false)
    private Integer outputIndex;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal amount;

    @Column(nullable = false)
    private Integer confirmations;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private IncomingPayment.Status status;

    @Column(name = "credit_operation_id", length = 36)
    private String creditOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "settle_status", nullable = false, length = 16)
    private IncomingPayment.SettleStatus settleStatus;

    @Column(name = "settlement_conversion_id", length = 36)
    private String settlementConversionId;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IncomingPaymentEntity() {}

    static IncomingPaymentEntity fromDomain(IncomingPayment p) {
        IncomingPaymentEntity e = new IncomingPaymentEntity();
        e.id = p.id().toString();
        e.merchantId = p.merchantId();
        e.txHash = p.txHash();
        e.outputIndex = p.outputIndex();
        e.currency = p.amount().currency();
        e.amount = p.amount().amount();
        e.confirmations = p.confirmations();
        e.status = p.status();
        e.creditOperationId = p.creditOperationId() == null ? null : p.creditOperationId().toString();
        e.settleStatus = p.settleStatus();
        e.settlementConversionId =
                p.settlementConversionId() == null ? null : p.settlementConversionId().toString();
        e.firstSeenAt = p.firstSeenAt();
        e.confirmedAt = p.confirmedAt();
        e.updatedAt = p.updatedAt();
        return e;
    }

    IncomingPayment toDomain() {
        return new IncomingPayment(UUID.fromString(id), merchantId, txHash, outputIndex,
                Money.of(amount, currency), confirmations, status,
                creditOperationId == null ? null : UUID.fromString(creditOperationId),
                settleStatus,
                settlementConversionId == null ? null : UUID.fromString(settlementConversionId),
                firstSeenAt, confirmedAt, updatedAt);
    }
}
