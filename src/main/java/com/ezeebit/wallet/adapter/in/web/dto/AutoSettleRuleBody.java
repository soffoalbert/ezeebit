package com.ezeebit.wallet.adapter.in.web.dto;

import com.ezeebit.wallet.domain.model.Currency;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * Upsert body for an auto-settle rule (Task 5). The source currency is a path variable; this
 * carries the fiat target, the percentage to convert, and whether the rule is active.
 */
public record AutoSettleRuleBody(
        @NotNull Currency targetCurrency,
        @NotNull @Positive @DecimalMax("100.0") BigDecimal percentage,
        boolean enabled) {
}
