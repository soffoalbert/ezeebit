package com.ezeebit.wallet;

import com.ezeebit.wallet.adapter.in.scheduling.OutboxRelay;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.RequestWithdrawalCommand;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.WithdrawalView;
import com.ezeebit.wallet.application.port.in.SubmitWithdrawalUseCase;
import com.ezeebit.wallet.domain.exception.NoPayoutRouteException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Task 6 — payout routing by country + currency + limit + health, with priority failover and
 * the guarantee that funds are never stuck. Seeds: za-swift-eft (ZAR, cap 1000, prio 1),
 * za-payfast (ZAR, prio 2), chain-usdt (any country). No KES partner exists.
 */
class PayoutRoutingIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;   // country ZA

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    RequestWithdrawalUseCase withdraw;
    @Autowired
    GetWithdrawalUseCase getWithdrawal;
    @Autowired
    SubmitWithdrawalUseCase submitWithdrawal;
    @Autowired
    OutboxRelay outboxRelay;

    private WithdrawalView requestZar(String amount, String key) {
        return withdraw.request(new RequestWithdrawalCommand(MERCHANT, Currency.ZAR,
                new BigDecimal(amount), "{\"accountNumber\":\"12345678\",\"bankCode\":\"632005\"}", key));
    }

    @Test
    void smallZarRoutesToPriorityOnePartner() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("5000.00"), "seed-a", "s"));
        WithdrawalView w = requestZar("500.00", "rt-small");

        outboxRelay.poll();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("COMPLETED"));

        WithdrawalView done = getWithdrawal.get(MERCHANT, w.withdrawalId());
        assertThat(done.partnerCode()).isEqualTo("za-swift-eft");
        assertThat(done.payoutReference()).startsWith("payout_za-swift-eft_");
        assertLedgerInvariantHolds();
    }

    @Test
    void largeZarBypassesLowLimitPartner() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("5000.00"), "seed-b", "s"));
        WithdrawalView w = requestZar("1500.00", "rt-large");   // over za-swift-eft's 1000 cap

        outboxRelay.poll();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("COMPLETED"));

        assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).partnerCode()).isEqualTo("za-payfast");
        assertLedgerInvariantHolds();
    }

    @Test
    void usdtRoutesToOnChainPartnerRegardlessOfCountry() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"), "seed-c", "s"));
        WithdrawalView w = withdraw.request(new RequestWithdrawalCommand(MERCHANT, Currency.USDT,
                new BigDecimal("40.000000"), "{\"address\":\"chain-address-0001\"}", "rt-usdt"));

        outboxRelay.poll();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("COMPLETED"));

        assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).partnerCode()).isEqualTo("chain-usdt");
        assertLedgerInvariantHolds();
    }

    @Test
    void unroutableCurrencyIsRejectedWithoutHoldingFunds() {
        assertThatThrownBy(() -> withdraw.request(new RequestWithdrawalCommand(MERCHANT, Currency.KES,
                new BigDecimal("100.00"), "{\"accountNumber\":\"12345678\",\"bankCode\":\"111\"}", "rt-kes")))
                .isInstanceOf(NoPayoutRouteException.class);

        // Nothing was held or scheduled.
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM withdrawal", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ledger_entry", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event", Long.class)).isZero();
    }

    @Test
    void allPartnersDownHoldsFundsThenCompletesWhenHealthy() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("5000.00"), "seed-d", "s"));
        WithdrawalView w = requestZar("500.00", "rt-down");

        jdbc.update("UPDATE payout_partner SET healthy = FALSE WHERE currency = 'ZAR'");
        outboxRelay.poll();   // submit throws RailUnavailable -> stays PENDING, funds held

        assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("PENDING");
        assertThat(balanceOf(MERCHANT, "ZAR")).isEqualByComparingTo("4500.00");   // held

        // Partners recover; make the backed-off event due and poll again.
        jdbc.update("UPDATE payout_partner SET healthy = TRUE WHERE currency = 'ZAR'");
        jdbc.update("UPDATE outbox_event SET next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE");
        outboxRelay.poll();

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("COMPLETED"));
        assertLedgerInvariantHolds();
    }

    @Test
    void expiredPendingIsFailedAndReleased() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR, new BigDecimal("5000.00"), "seed-e", "s"));
        WithdrawalView w = requestZar("500.00", "rt-expire");
        UUID id = UUID.fromString(w.withdrawalId());

        // Simulate a payout that could never be submitted: fail it past the deadline.
        submitWithdrawal.failExpired(id);

        assertThat(getWithdrawal.get(MERCHANT, w.withdrawalId()).status()).isEqualTo("FAILED");
        assertThat(balanceOf(MERCHANT, "ZAR")).isEqualByComparingTo("5000.00");   // funds released
        assertLedgerInvariantHolds();
    }
}
