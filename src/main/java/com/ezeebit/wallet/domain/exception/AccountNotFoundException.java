package com.ezeebit.wallet.domain.exception;

import com.ezeebit.wallet.domain.model.Currency;

public class AccountNotFoundException extends WalletException {
    public AccountNotFoundException(long merchantId, Currency currency) {
        super("ACCOUNT_NOT_FOUND",
                "merchant " + merchantId + " holds no " + currency + " account");
    }
}
