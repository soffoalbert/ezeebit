package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

/**
 * Shared record of the last rate accepted for a currency pair, backing the deviation
 * guard. Living in the database (rather than one node's memory) keeps the guard correct
 * across instances.
 */
public interface RateReferenceStore {

    Optional<BigDecimal> lastRate(Currency from, Currency to);

    void record(Currency from, Currency to, BigDecimal rate, Instant observedAt);
}
