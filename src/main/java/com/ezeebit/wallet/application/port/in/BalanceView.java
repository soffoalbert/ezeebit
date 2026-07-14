package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;

public record BalanceView(long merchantId, Currency currency, BigDecimal balance) {
    public static BalanceView of(Account account) {
        return new BalanceView(account.merchantId(), account.currency(), account.balance().amount());
    }
}
