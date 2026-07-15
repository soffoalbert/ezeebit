package com.ezeebit.wallet.application.port.in;

import java.util.UUID;

/**
 * Task 5 — settle (auto-convert) a confirmed incoming payment according to the merchant's
 * auto-settle rule. Driven by the outbox after an incoming payment confirms; idempotent on
 * the payment's settle-status state machine so a redelivery never double-converts.
 */
public interface ExecuteAutoSettleUseCase {

    void settle(UUID incomingPaymentId);
}
