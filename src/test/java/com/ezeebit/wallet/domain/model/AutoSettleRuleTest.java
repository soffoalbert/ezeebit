package com.ezeebit.wallet.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AutoSettleRuleTest {

    private AutoSettleRule rule(BigDecimal percentage) {
        return AutoSettleRule.of(1L, Currency.USDT, Currency.ZAR, percentage, true, Instant.now());
    }

    @Test
    void portionRoundsDownToSourceScale() {
        // 33.333333% of 100 USDT = 33.333333 (scale 6, rounded DOWN).
        Money portion = rule(new BigDecimal("33.3333")).portionOf(Money.of("100.000000", Currency.USDT));
        assertThat(portion).isEqualTo(Money.of("33.333300", Currency.USDT));
        assertThat(portion.currency()).isEqualTo(Currency.USDT);
    }

    @Test
    void hundredPercentIsIdentity() {
        Money portion = rule(new BigDecimal("100")).portionOf(Money.of("100.000000", Currency.USDT));
        assertThat(portion).isEqualTo(Money.of("100.000000", Currency.USDT));
    }

    @Test
    void tinyAmountCanRoundToZero() {
        // 1% of 0.000001 USDT rounds down to zero.
        Money portion = rule(new BigDecimal("1")).portionOf(Money.of("0.000001", Currency.USDT));
        assertThat(portion.isZero()).isTrue();
    }

    @Test
    void rejectsInvalidCurrenciesAndPercentages() {
        assertThatThrownBy(() -> AutoSettleRule.of(1L, Currency.ZAR, Currency.USDT,
                new BigDecimal("50"), true, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);   // source not stablecoin
        assertThatThrownBy(() -> AutoSettleRule.of(1L, Currency.USDT, Currency.USDC,
                new BigDecimal("50"), true, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);   // target not fiat
        assertThatThrownBy(() -> rule(new BigDecimal("0")))
                .isInstanceOf(IllegalArgumentException.class);   // percentage <= 0
        assertThatThrownBy(() -> rule(new BigDecimal("100.01")))
                .isInstanceOf(IllegalArgumentException.class);   // percentage > 100
    }

    @Test
    void portionRejectsMismatchedCurrency() {
        assertThatThrownBy(() -> rule(new BigDecimal("50")).portionOf(Money.of("100.000000", Currency.USDC)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
