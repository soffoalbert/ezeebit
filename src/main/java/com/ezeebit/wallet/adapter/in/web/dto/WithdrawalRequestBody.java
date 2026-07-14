package com.ezeebit.wallet.adapter.in.web.dto;

import com.ezeebit.wallet.domain.model.Currency;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WithdrawalRequestBody(
        @NotNull Currency currency,
        @NotNull @Positive BigDecimal amount,
        @NotNull JsonNode destination) {
}
