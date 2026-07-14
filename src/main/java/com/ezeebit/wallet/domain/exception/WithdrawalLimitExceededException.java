package com.ezeebit.wallet.domain.exception;

import com.ezeebit.wallet.domain.model.Money;

import java.math.BigDecimal;

public class WithdrawalLimitExceededException extends WalletException {
    public WithdrawalLimitExceededException(Money requested, BigDecimal max) {
        super("WITHDRAWAL_LIMIT_EXCEEDED",
                "requested " + requested + " exceeds the per-withdrawal limit of "
                        + max.toPlainString() + " " + requested.currency());
    }
}
