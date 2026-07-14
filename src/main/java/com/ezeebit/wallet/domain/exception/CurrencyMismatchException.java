package com.ezeebit.wallet.domain.exception;

import com.ezeebit.wallet.domain.model.Currency;

public class CurrencyMismatchException extends WalletException {
    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("CURRENCY_MISMATCH",
                "cannot operate across currencies: expected " + expected + " but got " + actual);
    }
}
