package com.ezeebit.wallet.domain.exception;

public class InvalidDestinationException extends WalletException {
    public InvalidDestinationException(String message) {
        super("INVALID_DESTINATION", message);
    }
}
