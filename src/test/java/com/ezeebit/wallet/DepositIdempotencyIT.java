package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.domain.exception.ConcurrentRequestException;
import com.ezeebit.wallet.domain.exception.IdempotencyConflictException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DepositIdempotencyIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    DepositFundsUseCase deposit;

    @Test
    void replayingSameKeyDepositsOnlyOnce() {
        DepositCommand cmd = new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("100.00"),
                "dep-key-1", "payday");

        deposit.deposit(cmd);
        deposit.deposit(cmd);   // retry with identical key + body
        deposit.deposit(cmd);

        assertThat(balanceOf(MERCHANT, "ZAR")).isEqualByComparingTo("100.00");
        assertLedgerInvariantHolds();
    }

    @Test
    void sameKeyDifferentBodyIsRejected() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("100.00"), "dep-key-2", null));
        assertThatThrownBy(() -> deposit.deposit(
                new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("999.00"), "dep-key-2", null)))
                .isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void concurrentSameKeyDepositsApplyOnce() throws Exception {
        int threads = 8;
        DepositCommand cmd = new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("25.000000"),
                "dep-key-concurrent", "burst");

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            tasks.add(() -> {
                try {
                    deposit.deposit(cmd);
                    return true;
                } catch (ConcurrentRequestException e) {
                    return false;   // lost the race; retry would hit the cache
                }
            });
        }
        List<Future<Boolean>> results = pool.invokeAll(tasks);
        pool.shutdown();
        for (Future<Boolean> f : results) {
            f.get();   // surface any unexpected exception
        }

        // Exactly one deposit effect regardless of how many requests raced.
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("25.000000");
        assertLedgerInvariantHolds();
    }
}
