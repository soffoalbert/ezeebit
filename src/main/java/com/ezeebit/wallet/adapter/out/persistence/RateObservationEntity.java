package com.ezeebit.wallet.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rate_observation")
class RateObservationEntity {

    @Id
    @Column(length = 24)
    private String pair;

    @Column(nullable = false, precision = 38, scale = 18)
    private BigDecimal rate;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    protected RateObservationEntity() {}

    RateObservationEntity(String pair, BigDecimal rate, Instant observedAt) {
        this.pair = pair;
        this.rate = rate;
        this.observedAt = observedAt;
    }

    BigDecimal getRate() {
        return rate;
    }
}
