package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.IncomingPaymentConflictException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IncomingPaymentTest {

    private static final int THRESHOLD = 3;

    private IncomingPayment observed(int confirmations) {
        return IncomingPayment.observed(1L, "tx-abc", 0,
                Money.of("100.000000", Currency.USDT), confirmations, Instant.now());
    }

    @Test
    void firstSightingIsPendingAndUnconfirmed() {
        IncomingPayment p = observed(1);
        assertThat(p.status()).isEqualTo(IncomingPayment.Status.PENDING);
        assertThat(p.settleStatus()).isEqualTo(IncomingPayment.SettleStatus.NONE);
        assertThat(p.confirm(THRESHOLD, UUID.randomUUID(), Instant.now())).isFalse();  // below threshold
    }

    @Test
    void confirmsExactlyOnceAtThreshold() {
        IncomingPayment p = observed(1);
        p.recordConfirmations(3, Instant.now());
        assertThat(p.confirm(THRESHOLD, UUID.randomUUID(), Instant.now())).isTrue();
        assertThat(p.status()).isEqualTo(IncomingPayment.Status.CONFIRMED);
        assertThat(p.settleStatus()).isEqualTo(IncomingPayment.SettleStatus.REQUESTED);
        // A second confirm is a no-op (single-shot credit).
        assertThat(p.confirm(THRESHOLD, UUID.randomUUID(), Instant.now())).isFalse();
    }

    @Test
    void confirmationsAreMonotonicMax() {
        IncomingPayment p = observed(5);
        assertThat(p.recordConfirmations(2, Instant.now())).isFalse();   // regressed -> no-op
        assertThat(p.confirmations()).isEqualTo(5);
        assertThat(p.recordConfirmations(5, Instant.now())).isFalse();   // duplicate -> no-op
        assertThat(p.recordConfirmations(6, Instant.now())).isTrue();    // advanced
        assertThat(p.confirmations()).isEqualTo(6);
    }

    @Test
    void outOfOrderNotificationsStillConfirmOnce() {
        IncomingPayment p = observed(5);   // first sighting already above threshold
        assertThat(p.confirm(THRESHOLD, UUID.randomUUID(), Instant.now())).isTrue();
        p.recordConfirmations(2, Instant.now());   // stale/regressed later notification
        assertThat(p.confirm(THRESHOLD, UUID.randomUUID(), Instant.now())).isFalse();
        assertThat(p.status()).isEqualTo(IncomingPayment.Status.CONFIRMED);
    }

    @Test
    void assertMatchesRejectsContradictingReplay() {
        IncomingPayment p = observed(1);
        p.assertMatches(1L, Money.of("100.000000", Currency.USDT));   // ok
        assertThatThrownBy(() -> p.assertMatches(1L, Money.of("999.000000", Currency.USDT)))
                .isInstanceOf(IncomingPaymentConflictException.class);
        assertThatThrownBy(() -> p.assertMatches(2L, Money.of("100.000000", Currency.USDT)))
                .isInstanceOf(IncomingPaymentConflictException.class);
    }

    @Test
    void settleAndSkipAreSingleShotFromRequested() {
        IncomingPayment p = observed(3);
        p.confirm(THRESHOLD, UUID.randomUUID(), Instant.now());
        assertThat(p.markSettled(UUID.randomUUID(), Instant.now())).isTrue();
        assertThat(p.settleStatus()).isEqualTo(IncomingPayment.SettleStatus.SETTLED);
        assertThat(p.markSettled(UUID.randomUUID(), Instant.now())).isFalse();  // redelivery no-op
        assertThat(p.markSkipped(Instant.now())).isFalse();                     // terminal
    }

    @Test
    void rejectsNonStablecoinOrNonPositive() {
        assertThatThrownBy(() -> IncomingPayment.observed(1L, "tx", 0,
                Money.of("10.00", Currency.ZAR), 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IncomingPayment.observed(1L, "tx", 0,
                Money.of("0.000000", Currency.USDT), 1, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
