package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * The external exchange-rate feed. Assumed to already exist; it can be slow and its
 * price keeps moving. Implementations may throw
 * {@link com.ezeebit.wallet.domain.exception.RateUnavailableException} on timeout or error.
 */
public interface ExchangeRateProvider {

    /** The mid-market rate such that {@code 1 unit of `from` = rate units of `to`}. */
    Rate getRate(Currency from, Currency to);

    record Rate(Currency from, Currency to, BigDecimal rate, Instant asOf) {}
}
