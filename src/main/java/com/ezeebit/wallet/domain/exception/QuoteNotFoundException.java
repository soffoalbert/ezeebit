package com.ezeebit.wallet.domain.exception;

import java.util.UUID;

public class QuoteNotFoundException extends WalletException {
    public QuoteNotFoundException(UUID quoteId) {
        super("QUOTE_NOT_FOUND", "quote " + quoteId + " does not exist");
    }
}
