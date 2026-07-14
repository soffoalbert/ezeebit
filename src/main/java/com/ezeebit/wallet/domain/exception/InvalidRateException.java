package com.ezeebit.wallet.domain.exception;

public class InvalidRateException extends WalletException {
    public InvalidRateException(String message) {
        super("INVALID_RATE", message);
    }
}
