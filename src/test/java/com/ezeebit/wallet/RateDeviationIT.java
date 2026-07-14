package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase.QuoteCommand;
import com.ezeebit.wallet.domain.exception.InvalidRateException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rate-deviation guard reads its reference from the shared store, so a feed value that
 * jumps far from the last accepted rate is rejected before it can be quoted.
 */
class RateDeviationIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    QuoteConversionUseCase quoteConversion;

    @Test
    void aWildRateMoveIsRejected() {
        // Seed a last-accepted USDT:ZAR rate far below the stub's ~18, so the next quote
        // deviates well beyond the configured threshold.
        jdbc.update("INSERT INTO rate_observation (pair, rate, observed_at) VALUES (?,?,?)",
                "USDT:ZAR", new BigDecimal("1.000000000000000000"), Timestamp.from(Instant.now()));

        assertThatThrownBy(() -> quoteConversion.quote(new QuoteCommand(
                MERCHANT, Currency.USDT, Currency.ZAR, new BigDecimal("100.000000"))))
                .isInstanceOf(InvalidRateException.class);
    }
}
