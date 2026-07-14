package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.CurrencyMismatchException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    @Test
    void normalisesToCurrencyScale() {
        assertThat(Money.of("10.5", Currency.ZAR).amount()).isEqualByComparingTo("10.50");
        assertThat(Money.of("10.123456789", Currency.USDT).amount()).isEqualByComparingTo("10.123457");
    }

    @Test
    void addingSameCurrencyWorks() {
        Money sum = Money.of("10.00", Currency.ZAR).add(Money.of("5.25", Currency.ZAR));
        assertThat(sum.amount()).isEqualByComparingTo("15.25");
    }

    @Test
    void mixingCurrenciesIsRejected() {
        assertThatThrownBy(() -> Money.of("1", Currency.ZAR).add(Money.of("1", Currency.USDT)))
                .isInstanceOf(CurrencyMismatchException.class);
        assertThatThrownBy(() -> Money.of("1", Currency.ZAR).isGreaterThan(Money.of("1", Currency.NGN)))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void comparisonsBehave() {
        assertThat(Money.of("10", Currency.ZAR).isGreaterThan(Money.of("5", Currency.ZAR))).isTrue();
        assertThat(Money.of("5", Currency.ZAR).isLessThan(Money.of("10", Currency.ZAR))).isTrue();
        assertThat(Money.zero(Currency.ZAR).isZero()).isTrue();
    }

    @Test
    void multiplyByRateRoundsToScale() {
        Money converted = Money.of("100", Currency.USDT).multiply(new BigDecimal("18.20"));
        assertThat(converted.amount()).isEqualByComparingTo("1820.000000");
    }
}
