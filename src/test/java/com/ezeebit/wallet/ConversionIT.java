package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase.ConversionView;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase.ExecuteConversionCommand;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase.QuoteCommand;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase.QuoteView;
import com.ezeebit.wallet.domain.exception.QuoteAlreadyUsedException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversionIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    QuoteConversionUseCase quoteConversion;
    @Autowired
    ExecuteConversionUseCase executeConversion;

    @Test
    void quoteThenExecuteMovesBothBalances() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-usdt", "seed"));

        QuoteView quote = quoteConversion.quote(new QuoteCommand(
                MERCHANT, Currency.USDT, Currency.ZAR, new BigDecimal("100.000000")));

        ConversionView conversion = executeConversion.execute(new ExecuteConversionCommand(
                MERCHANT, quote.quoteId(), "conv-key-1"));

        assertThat(conversion.status()).isEqualTo("EXECUTED");
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("0.000000");
        assertThat(balanceOf(MERCHANT, "ZAR")).isEqualByComparingTo(quote.toAmount());
        assertLedgerInvariantHolds();
    }

    @Test
    void executeIsIdempotentOnKey() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-usdt-2", "seed"));
        QuoteView quote = quoteConversion.quote(new QuoteCommand(
                MERCHANT, Currency.USDT, Currency.ZAR, new BigDecimal("50.000000")));

        ConversionView first = executeConversion.execute(
                new ExecuteConversionCommand(MERCHANT, quote.quoteId(), "conv-key-2"));
        ConversionView replay = executeConversion.execute(
                new ExecuteConversionCommand(MERCHANT, quote.quoteId(), "conv-key-2"));

        assertThat(replay.conversionId()).isEqualTo(first.conversionId());
        // Only one conversion effect: 50 USDT converted, 50 remain.
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("50.000000");
        assertLedgerInvariantHolds();
    }

    @Test
    void aQuoteCannotBeSpentTwiceWithDifferentKeys() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-usdt-3", "seed"));
        QuoteView quote = quoteConversion.quote(new QuoteCommand(
                MERCHANT, Currency.USDT, Currency.ZAR, new BigDecimal("30.000000")));

        executeConversion.execute(new ExecuteConversionCommand(MERCHANT, quote.quoteId(), "conv-key-3a"));
        assertThatThrownBy(() -> executeConversion.execute(
                new ExecuteConversionCommand(MERCHANT, quote.quoteId(), "conv-key-3b")))
                .isInstanceOf(QuoteAlreadyUsedException.class);
    }
}
