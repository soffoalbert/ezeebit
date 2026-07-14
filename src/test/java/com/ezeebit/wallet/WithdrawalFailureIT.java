package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.RequestWithdrawalCommand;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.WithdrawalView;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

/**
 * With the stub rail forced to fail, a held withdrawal must end FAILED and the held
 * funds must be released back to the merchant's balance.
 */
@TestPropertySource(properties = {
        "wallet.payout.failure-rate=1.0",
        "wallet.payout.settlement-delay-ms=50"
})
class WithdrawalFailureIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    RequestWithdrawalUseCase withdraw;
    @Autowired
    GetWithdrawalUseCase getWithdrawal;

    @Test
    void failedPayoutReleasesHeldFunds() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-fail", "seed"));

        WithdrawalView requested = withdraw.request(new RequestWithdrawalCommand(
                MERCHANT, Currency.USDT, new BigDecimal("40.000000"), "{\"addr\":\"x\"}", "wd-fail-1"));

        // Immediately after the request the funds are held.
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("60.000000");

        // The async rail settles as a failure; wait for the compensating release.
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            var view = getWithdrawal.get(MERCHANT, requested.withdrawalId());
            assertThat(view.status()).isEqualTo("FAILED");
        });

        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("100.000000");
        assertLedgerInvariantHolds();
    }
}
