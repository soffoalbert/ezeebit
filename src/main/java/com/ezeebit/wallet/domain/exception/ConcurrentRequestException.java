package com.ezeebit.wallet.domain.exception;

public class ConcurrentRequestException extends WalletException {
    public ConcurrentRequestException(String key) {
        super("CONCURRENT_REQUEST",
                "a concurrent request with key '" + key + "' is being processed; retry");
    }
}
