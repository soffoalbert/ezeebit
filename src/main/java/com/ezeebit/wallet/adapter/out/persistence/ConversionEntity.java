package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Conversion;
import com.ezeebit.wallet.domain.model.Currency;
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
@Table(name = "conversion")
class ConversionEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Column(name = "quote_id", nullable = false, length = 36)
    private String quoteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_currency", nullable = false, length = 10)
    private Currency fromCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_currency", nullable = false, length = 10)
    private Currency toCurrency;

    @Column(name = "from_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal fromAmount;

    @Column(name = "to_amount", nullable = false, precision = 38, scale = 18)
    private BigDecimal toAmount;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal rate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Conversion.Status status;

    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "executed_at")
    private Instant executedAt;

    protected ConversionEntity() {}

    static ConversionEntity fromDomain(Conversion c) {
        ConversionEntity e = new ConversionEntity();
        e.id = c.id().toString();
        e.merchantId = c.merchantId();
        e.quoteId = c.quoteId().toString();
        e.fromCurrency = c.fromAmount().currency();
        e.toCurrency = c.toAmount().currency();
        e.fromAmount = c.fromAmount().amount();
        e.toAmount = c.toAmount().amount();
        e.rate = c.rate();
        e.status = c.status();
        e.operationId = c.operationId().toString();
        e.createdAt = c.createdAt();
        e.executedAt = c.executedAt();
        return e;
    }

    Conversion toDomain() {
        return new Conversion(UUID.fromString(id), merchantId, UUID.fromString(quoteId),
                Money.of(fromAmount, fromCurrency), Money.of(toAmount, toCurrency),
                rate, status, UUID.fromString(operationId), createdAt, executedAt);
    }
}
