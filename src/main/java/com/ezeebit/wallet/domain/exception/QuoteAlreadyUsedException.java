package com.ezeebit.wallet.domain.exception;

import java.util.UUID;

public class QuoteAlreadyUsedException extends WalletException {
    public QuoteAlreadyUsedException(UUID quoteId) {
        super("QUOTE_ALREADY_USED", "quote " + quoteId + " has already been executed");
    }
}
