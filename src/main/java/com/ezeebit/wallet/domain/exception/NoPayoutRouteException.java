package com.ezeebit.wallet.domain.exception;

import com.ezeebit.wallet.domain.model.Currency;

/**
 * No payout partner can ever serve this payout (no partner for the merchant's country and
 * currency, or the amount exceeds every partner's limit) — a terminal, structural condition.
 * Surfaced as 422.
 */
public class NoPayoutRouteException extends WalletException {
    public NoPayoutRouteException(String country, Currency currency) {
        super("NO_PAYOUT_ROUTE",
                "no payout route for country=" + country + " currency=" + currency);
    }
}
