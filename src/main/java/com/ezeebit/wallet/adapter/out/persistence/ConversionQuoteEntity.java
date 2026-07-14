package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.Quote;
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
@Table(name = "conversion_quote")
class ConversionQuoteEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

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
    private Quote.Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected ConversionQuoteEntity() {}

    static ConversionQuoteEntity fromDomain(Quote q) {
        ConversionQuoteEntity e = new ConversionQuoteEntity();
        e.id = q.id().toString();
        e.merchantId = q.merchantId();
        e.fromCurrency = q.fromAmount().currency();
        e.toCurrency = q.toAmount().currency();
        e.fromAmount = q.fromAmount().amount();
        e.toAmount = q.toAmount().amount();
        e.rate = q.rate();
        e.status = q.status();
        e.createdAt = q.createdAt();
        e.expiresAt = q.expiresAt();
        return e;
    }

    Quote toDomain() {
        return new Quote(UUID.fromString(id), merchantId,
                Money.of(fromAmount, fromCurrency), Money.of(toAmount, toCurrency),
                rate, status, createdAt, expiresAt);
    }
}
