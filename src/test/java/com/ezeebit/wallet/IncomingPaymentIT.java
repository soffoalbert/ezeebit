package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.BalanceView;
import com.ezeebit.wallet.application.port.in.GetBalancesUseCase;
import com.ezeebit.wallet.application.port.in.GetIncomingPaymentsUseCase;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase.NotifyIncomingPaymentCommand;
import com.ezeebit.wallet.domain.exception.IncomingPaymentConflictException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Task 4 — the pending → available lifecycle of an incoming crypto payment, driven through
 * at-least-once, out-of-order confirmation notifications.
 */
class IncomingPaymentIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    NotifyIncomingPaymentUseCase notify;
    @Autowired
    GetBalancesUseCase getBalances;
    @Autowired
    GetIncomingPaymentsUseCase getIncomingPayments;

    private BigDecimal pendingOf(long merchant, Currency currency) {
        return getBalances.balancesOf(merchant).stream()
                .filter(b -> b.currency() == currency)
                .map(BalanceView::pendingIncoming)
                .findFirst().orElse(BigDecimal.ZERO);
    }

    private long incomingCreditCount(String txHash) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM ledger_entry WHERE type = 'INCOMING_CREDIT' AND reference LIKE ?",
                Long.class, "incoming " + txHash + "%");
    }

    @Test
    void pendingIsVisibleButNotSpendableUntilThreshold() {
        String tx = "0xpending01";
        notify.notify(new NotifyIncomingPaymentCommand(MERCHANT, tx, 0, Currency.USDT,
                new BigDecimal("100.000000"), 1));

        // Visible as pending, but not spendable and not on the ledger.
        assertThat(pendingOf(MERCHANT, Currency.USDT)).isEqualByComparingTo("100.000000");
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(incomingCreditCount(tx)).isZero();

        // Reaching the threshold credits the funds exactly once.
        notify.notify(new NotifyIncomingPaymentCommand(MERCHANT, tx, 0, Currency.USDT,
                new BigDecimal("100.000000"), 3));

        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("100.000000");
        assertThat(pendingOf(MERCHANT, Currency.USDT)).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(incomingCreditCount(tx)).isEqualTo(1L);
        assertLedgerInvariantHolds();
    }

    @Test
    void replayAndRegressionAreNoOpsAndCreditOnce() {
        String tx = "0xreplay01";
        notify.notify(cmd(tx, 3));   // confirm immediately
        notify.notify(cmd(tx, 3));   // exact duplicate
        notify.notify(cmd(tx, 2));   // regressed count
        notify.notify(cmd(tx, 5));   // more confirmations, already confirmed

        assertThat(incomingCreditCount(tx)).isEqualTo(1L);
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("100.000000");
        assertLedgerInvariantHolds();
    }

    @Test
    void outOfOrderHighThenLowCreditsOnce() {
        String tx = "0xooo01";
        notify.notify(cmd(tx, 5));   // arrives already past threshold
        notify.notify(cmd(tx, 2));   // a laggard lower count

        assertThat(incomingCreditCount(tx)).isEqualTo(1L);
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("100.000000");
        assertLedgerInvariantHolds();
    }

    @Test
    void contradictoryReplayIsRejected() {
        String tx = "0xconflict01";
        notify.notify(cmd(tx, 1));

        assertThatThrownBy(() -> notify.notify(new NotifyIncomingPaymentCommand(
                MERCHANT, tx, 0, Currency.USDT, new BigDecimal("200.000000"), 2)))
                .isInstanceOf(IncomingPaymentConflictException.class);
    }

    private NotifyIncomingPaymentCommand cmd(String tx, int confirmations) {
        return new NotifyIncomingPaymentCommand(MERCHANT, tx, 0, Currency.USDT,
                new BigDecimal("100.000000"), confirmations);
    }
}
