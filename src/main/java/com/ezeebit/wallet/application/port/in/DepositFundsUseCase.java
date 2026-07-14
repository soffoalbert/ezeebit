package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;

public interface DepositFundsUseCase {

    BalanceView deposit(DepositCommand command);

    record DepositCommand(long merchantId, Currency currency, BigDecimal amount,
                          String idempotencyKey, String reference) {}
}
