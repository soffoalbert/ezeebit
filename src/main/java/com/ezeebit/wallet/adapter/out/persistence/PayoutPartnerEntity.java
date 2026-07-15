package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.PayoutPartner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

@Entity
@Table(name = "payout_partner")
class PayoutPartnerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 2)
    private String country;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Currency currency;

    @Column(name = "per_tx_limit", precision = 38, scale = 18)
    private BigDecimal perTxLimit;

    @Column(nullable = false)
    private boolean healthy;

    @Column(nullable = false)
    private int priority;

    protected PayoutPartnerEntity() {}

    PayoutPartner toDomain() {
        return new PayoutPartner(id, code, name, country, currency, perTxLimit, healthy, priority);
    }
}
