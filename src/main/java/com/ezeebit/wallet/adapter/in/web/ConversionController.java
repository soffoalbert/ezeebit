package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.ExecuteConversionRequest;
import com.ezeebit.wallet.adapter.in.web.dto.QuoteRequest;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase.ConversionView;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase.QuoteView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 2 — convert one currency into another. Two steps: request a quote (locks a rate
 * for a short TTL), then execute against that quote id.
 */
@RestController
@RequestMapping("/merchants/{merchantId}/conversions")
class ConversionController {

    private final QuoteConversionUseCase quoteConversion;
    private final ExecuteConversionUseCase executeConversion;

    ConversionController(QuoteConversionUseCase quoteConversion,
                         ExecuteConversionUseCase executeConversion) {
        this.quoteConversion = quoteConversion;
        this.executeConversion = executeConversion;
    }

    @PostMapping("/quotes")
    QuoteView quote(@PathVariable long merchantId, @Valid @RequestBody QuoteRequest body) {
        return quoteConversion.quote(new QuoteConversionUseCase.QuoteCommand(
                merchantId, body.fromCurrency(), body.toCurrency(), body.amount()));
    }

    @PostMapping
    ConversionView execute(@PathVariable long merchantId,
                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                           @Valid @RequestBody ExecuteConversionRequest body) {
        return executeConversion.execute(new ExecuteConversionUseCase.ExecuteConversionCommand(
                merchantId, body.quoteId(), idempotencyKey));
    }
}
