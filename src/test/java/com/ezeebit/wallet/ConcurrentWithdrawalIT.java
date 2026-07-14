package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.RequestWithdrawalCommand;
import com.ezeebit.wallet.domain.exception.InsufficientFundsException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ConcurrentWithdrawalIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    DepositFundsUseCase deposit;

    @Autowired
    RequestWithdrawalUseCase withdraw;

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        // Fund exactly enough for 10 withdrawals of 10 USDT.
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-1", "seed"));

        int attempts = 20;   // twice as many requests as the balance can satisfy
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            final int n = i;
            tasks.add(() -> {
                try {
                    withdraw.request(new RequestWithdrawalCommand(MERCHANT, Currency.USDT,
                            new BigDecimal("10.000000"),
                            "{\"address\":\"chain-addr-" + String.format("%06d", n) + "\"}",
                            "wd-" + n));
                    accepted.incrementAndGet();
                } catch (InsufficientFundsException e) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        List<Future<Void>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        for (Future<Void> f : futures) {
            f.get();
        }

        // Exactly the affordable subset succeeds; the balance is fully held, never negative.
        assertThat(accepted.get()).isEqualTo(10);
        assertThat(rejected.get()).isEqualTo(10);
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("0.000000");
        assertLedgerInvariantHolds();
    }

    @Test
    void duplicateWithdrawalKeyIsPaidOutOnce() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-2", "seed"));

        RequestWithdrawalCommand cmd = new RequestWithdrawalCommand(MERCHANT, Currency.USDT,
                new BigDecimal("40.000000"), "{\"address\":\"chain-address-0001\"}", "wd-dup");

        var first = withdraw.request(cmd);
        var second = withdraw.request(cmd);   // duplicate submit (same key + body)

        assertThat(second.withdrawalId()).isEqualTo(first.withdrawalId());
        // Only one hold of 40 was applied.
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("60.000000");
        assertLedgerInvariantHolds();
    }
}
