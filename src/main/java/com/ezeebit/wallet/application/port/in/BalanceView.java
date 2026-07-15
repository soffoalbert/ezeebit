package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Account;

import java.math.BigDecimal;

/**
 * A merchant's balance in one currency. {@code balance} is the spendable balance (the ledger
 * total); {@code pendingIncoming} is on-chain money seen but not yet confirmed (Task 4) —
 * visible but not spendable, and never part of the ledger.
 */
public record BalanceView(long merchantId, com.ezeebit.wallet.domain.model.Currency currency,
                          BigDecimal balance, BigDecimal pendingIncoming) {

    public static BalanceView of(Account account) {
        return new BalanceView(account.merchantId(), account.currency(),
                account.balance().amount(), BigDecimal.ZERO);
    }
}
