package com.ezeebit.wallet.domain.exception;

/**
 * Every eligible payout partner is temporarily unavailable (unhealthy or rejected the request
 * synchronously). Transient: the withdrawal stays PENDING with funds held, and the outbox /
 * recovery sweeper retries until a partner recovers. Surfaced as 503.
 */
public class RailUnavailableException extends WalletException {
    public RailUnavailableException(String detail) {
        super("PARTNER_UNAVAILABLE", detail);
    }
}
