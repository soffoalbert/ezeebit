package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;

public interface RequestWithdrawalUseCase {

    WithdrawalView request(RequestWithdrawalCommand command);

    record RequestWithdrawalCommand(long merchantId, Currency currency, BigDecimal amount,
                                    String destination, String idempotencyKey) {}

    record WithdrawalView(String withdrawalId, long merchantId, Currency currency,
                          BigDecimal amount, String status, String payoutReference,
                          String failureReason, Instant createdAt, Instant updatedAt) {}
}
