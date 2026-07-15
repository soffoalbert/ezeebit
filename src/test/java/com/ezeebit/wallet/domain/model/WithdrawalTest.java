package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.IllegalWithdrawalStateException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalTest {

    private Withdrawal pending() {
        return Withdrawal.requested(1L, "key-1", Money.of("50", Currency.USDT), "{\"addr\":\"x\"}", Instant.now());
    }

    @Test
    void happyPathTransitions() {
        Withdrawal w = pending();
        w.markSubmitted("ref-1", "za-payfast", Instant.now());
        assertThat(w.status()).isEqualTo(Withdrawal.Status.SUBMITTED);
        assertThat(w.markCompleted(Instant.now())).isTrue();
        assertThat(w.status()).isEqualTo(Withdrawal.Status.COMPLETED);
    }

    @Test
    void duplicateCompletionIsIdempotentNoOp() {
        Withdrawal w = pending();
        w.markSubmitted("ref-1", "za-payfast", Instant.now());
        assertThat(w.markCompleted(Instant.now())).isTrue();
        assertThat(w.markCompleted(Instant.now())).isFalse();   // second callback ignored
    }

    @Test
    void duplicateFailureIsIdempotentNoOp() {
        Withdrawal w = pending();
        w.markSubmitted("ref-1", "za-payfast", Instant.now());
        assertThat(w.markFailed("nope", Instant.now())).isTrue();
        assertThat(w.markFailed("nope", Instant.now())).isFalse();
        assertThat(w.status()).isEqualTo(Withdrawal.Status.FAILED);
    }

    @Test
    void cannotCompleteAfterFailure() {
        Withdrawal w = pending();
        w.markSubmitted("ref-1", "za-payfast", Instant.now());
        w.markFailed("nope", Instant.now());
        assertThatThrownBy(() -> w.markCompleted(Instant.now()))
                .isInstanceOf(IllegalWithdrawalStateException.class);
    }
}
