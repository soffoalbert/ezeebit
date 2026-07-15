package com.ezeebit.wallet;

import com.ezeebit.wallet.adapter.in.scheduling.OutboxRelay;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase.NotifyIncomingPaymentCommand;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 5 — auto-settle converts a merchant-configured percentage of a confirmed incoming
 * payment into local fiat, at market rate, driven by the outbox. Seeded rule: merchant 1
 * converts 50% of every incoming USDT to ZAR.
 */
class AutoSettleIT extends AbstractIntegrationTest {

    @Autowired
    NotifyIncomingPaymentUseCase notify;
    @Autowired
    OutboxRelay outboxRelay;

    private void confirmPayment(long merchant, String tx, String amount) {
        notify.notify(new NotifyIncomingPaymentCommand(merchant, tx, 0, Currency.USDT,
                new BigDecimal(amount), 3));
    }

    @Test
    void confirmedPaymentIsHalfConvertedToZar() {
        confirmPayment(1L, "0xsettle01", "100.000000");
        outboxRelay.poll();   // dispatch INCOMING_PAYMENT_CONFIRMED -> auto-settle

        Map<String, Object> payment = jdbc.queryForMap(
                "SELECT settle_status, settlement_conversion_id FROM incoming_payment WHERE tx_hash = '0xsettle01'");
        assertThat(payment.get("settle_status")).isEqualTo("SETTLED");
        String conversionId = (String) payment.get("settlement_conversion_id");
        assertThat(conversionId).isNotNull();

        Map<String, Object> conversion = jdbc.queryForMap(
                "SELECT quote_id, trigger_type, incoming_payment_id, from_currency, to_currency, from_amount "
                        + "FROM conversion WHERE id = ?", conversionId);
        assertThat(conversion.get("quote_id")).isNull();
        assertThat(conversion.get("trigger_type")).isEqualTo("AUTO_SETTLE");
        assertThat(conversion.get("incoming_payment_id")).isNotNull();
        assertThat(conversion.get("from_currency")).isEqualTo("USDT");
        assertThat(conversion.get("to_currency")).isEqualTo("ZAR");
        assertThat((BigDecimal) conversion.get("from_amount")).isEqualByComparingTo("50.000000");

        // Half the USDT was spent; the ZAR proceeds landed (market rate ~18.2, minus spread).
        assertThat(balanceOf(1L, "USDT")).isEqualByComparingTo("50.000000");
        assertThat(balanceOf(1L, "ZAR")).isGreaterThan(new BigDecimal("800.00"));
        assertLedgerInvariantHolds();
    }

    @Test
    void redeliveryDoesNotConvertTwice() {
        confirmPayment(1L, "0xsettle02", "100.000000");
        outboxRelay.poll();
        // Force the processed event back to pending and replay it (UTC_TIMESTAMP matches the relay clock).
        jdbc.update("UPDATE outbox_event SET status = 'PENDING', "
                + "next_attempt_at = UTC_TIMESTAMP(6) - INTERVAL 1 MINUTE "
                + "WHERE event_type = 'INCOMING_PAYMENT_CONFIRMED'");
        outboxRelay.poll();

        Long conversions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversion WHERE trigger_type = 'AUTO_SETTLE'", Long.class);
        assertThat(conversions).isEqualTo(1L);
        assertThat(balanceOf(1L, "USDT")).isEqualByComparingTo("50.000000");
        assertLedgerInvariantHolds();
    }

    @Test
    void merchantWithoutRuleIsSkipped() {
        confirmPayment(2L, "0xsettle03", "100.000000");   // merchant 2 has no auto-settle rule
        outboxRelay.poll();

        String settleStatus = jdbc.queryForObject(
                "SELECT settle_status FROM incoming_payment WHERE tx_hash = '0xsettle03'", String.class);
        assertThat(settleStatus).isEqualTo("SKIPPED");
        Long conversions = jdbc.queryForObject(
                "SELECT COUNT(*) FROM conversion WHERE trigger_type = 'AUTO_SETTLE'", Long.class);
        assertThat(conversions).isZero();
        // Full amount stays spendable in USDT.
        assertThat(balanceOf(2L, "USDT")).isEqualByComparingTo("100.000000");
        assertLedgerInvariantHolds();
    }
}
