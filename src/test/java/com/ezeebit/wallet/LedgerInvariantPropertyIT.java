package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase;
import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase.ExecuteConversionCommand;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase.QuoteCommand;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase.QuoteView;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.RequestWithdrawalCommand;
import com.ezeebit.wallet.domain.exception.WalletException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Property-style test: hammer the wallet with a randomised mix of deposits, conversions,
 * and withdrawals, then assert the core invariant still holds. Any domain-level rejection
 * (insufficient funds, limits, expired quote, …) is an expected outcome, not a failure —
 * what must never happen is the ledger and the cached balance disagreeing.
 */
class LedgerInvariantPropertyIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;
    private static final Currency[] CURRENCIES = {Currency.ZAR, Currency.USDT, Currency.USDC, Currency.NGN};

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    QuoteConversionUseCase quoteConversion;
    @Autowired
    ExecuteConversionUseCase executeConversion;
    @Autowired
    RequestWithdrawalUseCase withdraw;

    @Test
    void invariantHoldsUnderRandomisedOperations() {
        Random rnd = new Random(20260714L);   // fixed seed → reproducible

        // Seed some starting balance so conversions/withdrawals have something to move.
        for (Currency c : CURRENCIES) {
            deposit.deposit(new DepositCommand(MERCHANT, c, scaled("500", c), "seed-" + c, "seed"));
        }

        for (int i = 0; i < 120; i++) {
            try {
                switch (rnd.nextInt(3)) {
                    case 0 -> doDeposit(rnd, i);
                    case 1 -> doWithdraw(rnd, i);
                    default -> doConvert(rnd, i);
                }
            } catch (WalletException expected) {
                // Domain-level rejections are fine — the invariant must still hold.
            }
        }

        assertLedgerInvariantHolds();
    }

    private void doDeposit(Random rnd, int i) {
        Currency c = pick(rnd);
        deposit.deposit(new DepositCommand(MERCHANT, c, scaled(String.valueOf(1 + rnd.nextInt(50)), c),
                "dep-" + i, "r"));
    }

    private void doWithdraw(Random rnd, int i) {
        Currency c = pick(rnd);
        withdraw.request(new RequestWithdrawalCommand(MERCHANT, c,
                scaled(String.valueOf(1 + rnd.nextInt(80)), c), destinationFor(c, i), "wd-" + i));
    }

    private void doConvert(Random rnd, int i) {
        Currency from = pick(rnd);
        Currency to = pick(rnd);
        if (from == to) {
            return;
        }
        QuoteView quote = quoteConversion.quote(new QuoteCommand(MERCHANT, from, to,
                scaled(String.valueOf(1 + rnd.nextInt(40)), from)));
        executeConversion.execute(new ExecuteConversionCommand(MERCHANT, quote.quoteId(), "conv-" + i));
    }

    private Currency pick(Random rnd) {
        return CURRENCIES[rnd.nextInt(CURRENCIES.length)];
    }

    private BigDecimal scaled(String whole, Currency c) {
        return new BigDecimal(whole).setScale(c.scale());
    }

    private String destinationFor(Currency c, int i) {
        return c.isStablecoin()
                ? "{\"address\":\"chain-address-" + String.format("%06d", i) + "\"}"
                : "{\"accountNumber\":\"12345678\",\"bankCode\":\"ABC\"}";
    }
}
