package com.ezeebit.wallet.domain.exception;

import java.time.Instant;
import java.util.UUID;

public class QuoteExpiredException extends WalletException {
    public QuoteExpiredException(UUID quoteId, Instant expiredAt) {
        super("QUOTE_EXPIRED", "quote " + quoteId + " expired at " + expiredAt);
    }
}
