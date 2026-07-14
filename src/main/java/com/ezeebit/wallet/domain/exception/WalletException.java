package com.ezeebit.wallet.domain.exception;

/**
 * Base type for all domain-level failures. Each carries a stable machine-readable
 * {@code code} that the web layer maps to an RFC-7807 problem response.
 */
public abstract class WalletException extends RuntimeException {

    private final String code;

    protected WalletException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
