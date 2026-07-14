package com.ezeebit.wallet.domain.exception;

import com.ezeebit.wallet.domain.model.Withdrawal;

import java.util.UUID;

public class IllegalWithdrawalStateException extends WalletException {
    public IllegalWithdrawalStateException(UUID id, Withdrawal.Status from, Withdrawal.Status to) {
        super("ILLEGAL_WITHDRAWAL_STATE",
                "withdrawal " + id + " cannot move from " + from + " to " + to);
    }
}
