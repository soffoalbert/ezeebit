package com.ezeebit.wallet.domain.exception;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;

public class InsufficientFundsException extends WalletException {
    public InsufficientFundsException(long merchantId, Currency currency, Money available, Money requested) {
        super("INSUFFICIENT_FUNDS",
                "merchant " + merchantId + " has " + available + " but requested " + requested
                        + " in " + currency);
    }
}
