package com.ezeebit.wallet.domain.exception;

/** An admin operation referenced a payout partner code that does not exist. Surfaced as 404. */
public class PayoutPartnerNotFoundException extends WalletException {
    public PayoutPartnerNotFoundException(String code) {
        super("PARTNER_NOT_FOUND", "no payout partner with code '" + code + "'");
    }
}
