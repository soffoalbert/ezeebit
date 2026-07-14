package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.RequestWithdrawalCommand;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.WithdrawalView;
import com.ezeebit.wallet.adapter.in.scheduling.OutboxRelay;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The outbox relay — not an in-memory hook — carries a held withdrawal to submission and
 * settlement. Proves the durable path works end to end.
 */
class OutboxSubmissionIT extends AbstractIntegrationTest {

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
    void heldWithdrawalIsSubmittedAndSettledViaOutbox() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-ob", "seed"));

        WithdrawalView requested = withdraw.request(new RequestWithdrawalCommand(
                MERCHANT, Currency.USDT, new BigDecimal("40.000000"),
                "{\"address\":\"chain-address-000\"}", "wd-ob-1"));

        // An outbox row was written in the same transaction as the hold.
        Long outboxRows = jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class);
        assertThat(outboxRows).isEqualTo(1L);

        // Drive the relay: it claims the event and submits to the rail, which then settles.
        outboxRelay.poll();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertThat(getWithdrawal.get(MERCHANT, requested.withdrawalId()).status())
                        .isEqualTo("COMPLETED"));

        // The outbox event ends PROCESSED and the balance reflects the paid-out hold.
        String status = jdbc.queryForObject("SELECT status FROM outbox_event", String.class);
        assertThat(status).isEqualTo("PROCESSED");
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("60.000000");
        assertLedgerInvariantHolds();
    }
}
