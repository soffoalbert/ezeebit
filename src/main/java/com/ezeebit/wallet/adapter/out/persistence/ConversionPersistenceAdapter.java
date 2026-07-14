package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.ConversionRepository;
import com.ezeebit.wallet.domain.model.Conversion;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class ConversionPersistenceAdapter implements ConversionRepository {

    private final ConversionJpaRepository jpa;

    ConversionPersistenceAdapter(ConversionJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Conversion save(Conversion conversion) {
        return jpa.save(ConversionEntity.fromDomain(conversion)).toDomain();
    }

    @Override
    public Optional<Conversion> find(UUID id) {
        return jpa.findById(id.toString()).map(ConversionEntity::toDomain);
    }

    @Override
    public Optional<Conversion> findByQuoteId(UUID quoteId) {
        return jpa.findByQuoteId(quoteId.toString()).map(ConversionEntity::toDomain);
    }
}
