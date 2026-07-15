package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;

/**
 * Task 4 — the blockchain confirmation service notifies the wallet as an incoming payment
 * is first seen and becomes more final. Notifications are at-least-once and may arrive out
 * of order; the use case is idempotent on (txHash, outputIndex).
 */
public interface NotifyIncomingPaymentUseCase {

    void notify(NotifyIncomingPaymentCommand command);

    record NotifyIncomingPaymentCommand(long merchantId, String txHash, int outputIndex,
                                        Currency currency, BigDecimal amount, int confirmations) {}
}
