package com.ezeebit.wallet.adapter.in.web.dto;

import com.ezeebit.wallet.domain.model.Currency;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record DepositRequest(
        @NotNull Currency currency,
        @NotNull @Positive BigDecimal amount,
        String reference) {
}
