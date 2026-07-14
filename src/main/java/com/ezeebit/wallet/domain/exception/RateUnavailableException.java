package com.ezeebit.wallet.domain.exception;

public class RateUnavailableException extends WalletException {
    public RateUnavailableException(String message) {
        super("RATE_UNAVAILABLE", message);
    }
}
