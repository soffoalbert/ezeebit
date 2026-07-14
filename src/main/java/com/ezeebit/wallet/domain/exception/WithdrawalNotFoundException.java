package com.ezeebit.wallet.domain.exception;

public class WithdrawalNotFoundException extends WalletException {
    public WithdrawalNotFoundException(String id) {
        super("WITHDRAWAL_NOT_FOUND", "withdrawal " + id + " does not exist");
    }
}
