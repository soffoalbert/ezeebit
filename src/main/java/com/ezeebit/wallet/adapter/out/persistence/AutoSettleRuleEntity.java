package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.AutoSettleRule;
import com.ezeebit.wallet.domain.model.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "auto_settle_rule")
class AutoSettleRuleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "merchant_id", nullable = false)
    private Long merchantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_currency", nullable = false, length = 10)
    private Currency sourceCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_currency", nullable = false, length = 10)
    private Currency targetCurrency;

    @Column(nullable = false, precision = 7, scale = 4)
    private BigDecimal percentage;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutoSettleRuleEntity() {}

    static AutoSettleRuleEntity fromDomain(AutoSettleRule r) {
        AutoSettleRuleEntity e = new AutoSettleRuleEntity();
        e.id = r.id();
        e.merchantId = r.merchantId();
        e.sourceCurrency = r.sourceCurrency();
        e.targetCurrency = r.targetCurrency();
        e.percentage = r.percentage();
        e.enabled = r.enabled();
        e.createdAt = r.createdAt();
        e.updatedAt = r.updatedAt();
        return e;
    }

    /** Overlay a new desired state onto this managed row, preserving id + createdAt (upsert). */
    void applyUpsert(AutoSettleRule r) {
        this.targetCurrency = r.targetCurrency();
        this.percentage = r.percentage();
        this.enabled = r.enabled();
        this.updatedAt = r.updatedAt();
    }

    AutoSettleRule toDomain() {
        return new AutoSettleRule(id, merchantId, sourceCurrency, targetCurrency,
                percentage, enabled, createdAt, updatedAt);
    }
}
