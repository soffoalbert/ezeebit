package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.QuoteRepository;
import com.ezeebit.wallet.domain.model.Quote;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
class QuotePersistenceAdapter implements QuoteRepository {

    private final ConversionQuoteJpaRepository jpa;

    QuotePersistenceAdapter(ConversionQuoteJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Quote save(Quote quote) {
        return jpa.save(ConversionQuoteEntity.fromDomain(quote)).toDomain();
    }

    @Override
    public Optional<Quote> lockForUpdate(UUID id) {
        return jpa.lockById(id.toString()).map(ConversionQuoteEntity::toDomain);
    }

    @Override
    public Optional<Quote> find(UUID id) {
        return jpa.findById(id.toString()).map(ConversionQuoteEntity::toDomain);
    }
}
