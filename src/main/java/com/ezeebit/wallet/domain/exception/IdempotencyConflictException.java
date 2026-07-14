package com.ezeebit.wallet.domain.exception;

public class IdempotencyConflictException extends WalletException {
    public IdempotencyConflictException(String key) {
        super("IDEMPOTENCY_CONFLICT",
                "idempotency key '" + key + "' was already used with a different request body");
    }
}
