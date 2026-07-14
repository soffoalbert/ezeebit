package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Quote;

import java.util.Optional;
import java.util.UUID;

public interface QuoteRepository {

    Quote save(Quote quote);

    /** Load a quote for update, taking a row lock so it can only be consumed once. */
    Optional<Quote> lockForUpdate(UUID id);

    Optional<Quote> find(UUID id);
}
