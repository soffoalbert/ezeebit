package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public interface QuoteConversionUseCase {

    QuoteView quote(QuoteCommand command);

    record QuoteCommand(long merchantId, Currency fromCurrency, Currency toCurrency, BigDecimal fromAmount) {}

    record QuoteView(String quoteId, Currency fromCurrency, Currency toCurrency,
                     BigDecimal fromAmount, BigDecimal toAmount, BigDecimal rate,
                     Instant expiresAt) {}
}
