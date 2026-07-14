package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.util.Optional;

public interface WithdrawalLimitRepository {

    /** The per-(merchant, currency) cap on a single withdrawal, if one is configured. */
    Optional<BigDecimal> maxPerWithdrawal(long merchantId, Currency currency);
}
