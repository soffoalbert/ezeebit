package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public interface ExecuteConversionUseCase {

    ConversionView execute(ExecuteConversionCommand command);

    record ExecuteConversionCommand(long merchantId, String quoteId, String idempotencyKey) {}

    record ConversionView(String conversionId, String quoteId, String status,
                          Currency fromCurrency, Currency toCurrency,
                          BigDecimal fromAmount, BigDecimal toAmount, BigDecimal rate,
                          Instant executedAt) {}
}
