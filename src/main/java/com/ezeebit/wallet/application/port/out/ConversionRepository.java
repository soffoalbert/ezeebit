package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Conversion;

import java.util.Optional;
import java.util.UUID;

public interface ConversionRepository {

    Conversion save(Conversion conversion);

    Optional<Conversion> find(UUID id);

    Optional<Conversion> findByQuoteId(UUID quoteId);
}
