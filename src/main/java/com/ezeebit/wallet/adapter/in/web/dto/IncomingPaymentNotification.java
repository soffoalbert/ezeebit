package com.ezeebit.wallet.adapter.in.web.dto;

import com.ezeebit.wallet.domain.model.Currency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Body of a blockchain confirmation-service notification (Task 4). */
public record IncomingPaymentNotification(
        @NotNull Long merchantId,
        @NotBlank String txHash,
        @NotNull @PositiveOrZero Integer outputIndex,
        @NotNull Currency currency,
        @NotNull @Positive BigDecimal amount,
        @NotNull @PositiveOrZero Integer confirmations) {
}
