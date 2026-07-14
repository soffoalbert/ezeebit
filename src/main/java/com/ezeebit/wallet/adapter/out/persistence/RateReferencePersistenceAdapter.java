package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.RateReferenceStore;
import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

@Component
class RateReferencePersistenceAdapter implements RateReferenceStore {

    private final RateObservationJpaRepository jpa;

    RateReferencePersistenceAdapter(RateObservationJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<BigDecimal> lastRate(Currency from, Currency to) {
        return jpa.findById(pair(from, to)).map(RateObservationEntity::getRate);
    }

    @Override
    public void record(Currency from, Currency to, BigDecimal rate, Instant observedAt) {
        // Upsert by primary key (the pair).
        jpa.save(new RateObservationEntity(pair(from, to), rate, observedAt));
    }

    private static String pair(Currency from, Currency to) {
        return from + ":" + to;
    }
}
