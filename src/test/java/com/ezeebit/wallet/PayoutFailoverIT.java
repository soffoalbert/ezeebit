package com.ezeebit.wallet;

import com.ezeebit.wallet.adapter.in.scheduling.OutboxRelay;
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
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Task 6 — when the priority-1 partner is unavailable, routing fails over to the next partner
 * synchronously (before any callback), and the payout still completes.
 */
@TestPropertySource(properties = {
        "wallet.payout.partners.za-swift-eft.unavailable=true",
        "wallet.payout.settlement-delay-ms=400"
})
class PayoutFailoverIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    RequestWithdrawalUseCase withdraw;
    @Autowired
    GetWithdrawalUseCase getWithdrawal;
    @Autowired
    OutboxRelay outboxRelay;

    @Test
    void unavailablePriorityOnePartnerFailsOverToNext() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("5000.00"), "seed-fo", "s"));
        WithdrawalView w = withdraw.request(new RequestWithdrawalCommand(MERCHANT, Currency.ZAR,
                new BigDecimal("500.00"), "{\"accountNumber\":\"12345678\",\"bankCode\":\"632005\"}", "fo-1"));

        outboxRelay.poll();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("COMPLETED"));

        // za-swift-eft was unavailable, so it routed to za-payfast.
        assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).partnerCode()).isEqualTo("za-payfast");
        assertLedgerInvariantHolds();
    }
}
