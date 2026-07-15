package com.ezeebit.wallet;

import com.ezeebit.wallet.adapter.in.scheduling.OutboxRelay;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase.NotifyIncomingPaymentCommand;
import com.ezeebit.wallet.application.port.out.ExchangeRateProvider;
import com.ezeebit.wallet.domain.exception.RateUnavailableException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Task 5 — crash-safe retry when the rate feed is down at settle time. The rate provider
 * fails on the first attempt and recovers on the next: the payment stays REQUESTED with a
 * backed-off outbox row, and a later poll settles it. No money is lost or double-converted.
 */
class AutoSettleRetryIT extends AbstractIntegrationTest {

    @Autowired
    NotifyIncomingPaymentUseCase notify;
    @Autowired
    OutboxRelay outboxRelay;

    @MockBean
    ExchangeRateProvider rateProvider;

    @Test
    void settleRetriesAfterRateFeedRecovers() {
        // First auto-settle attempt: feed is down. Subsequent attempts: recovered.
        when(rateProvider.getRate(any(), any()))
                .thenThrow(new RateUnavailableException("feed down"))
                .thenReturn(new ExchangeRateProvider.Rate(
                        Currency.USDT, Currency.ZAR, new BigDecimal("18.20"), Instant.now()));

        notify.notify(new NotifyIncomingPaymentCommand(1L, "0xretry01", 0, Currency.USDT,
                new BigDecimal("100.000000"), 3));

        outboxRelay.poll();   // first dispatch throws -> rolls back, outbox backs off

        assertThat(jdbc.queryForObject(
                "SELECT settle_status FROM incoming_payment WHERE tx_hash = '0xretry01'", String.class))
                .isEqualTo("REQUESTED");
        assertThat(jdbc.queryForObject(
                "SELECT attempts FROM outbox_event WHERE event_type = 'INCOMING_PAYMENT_CONFIRMED'", Integer.class))
                .isGreaterThan(0);

        // Make the backed-off event due again and poll: the recovered feed lets it settle.
        // UTC_TIMESTAMP so the value is unambiguously UTC, matching the relay's clock.
        jdbc.update("UPDATE outbox_event SET next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE "
                + "WHERE event_type = 'INCOMING_PAYMENT_CONFIRMED'");
        outboxRelay.poll();

        assertThat(jdbc.queryForObject(
                "SELECT settle_status FROM incoming_payment WHERE tx_hash = '0xretry01'", String.class))
                .isEqualTo("SETTLED");
        assertThat(balanceOf(1L, "USDT")).isEqualByComparingTo("50.000000");
        assertThat(balanceOf(1L, "ZAR")).isGreaterThan(BigDecimal.ZERO);
        assertLedgerInvariantHolds();
    }
}
