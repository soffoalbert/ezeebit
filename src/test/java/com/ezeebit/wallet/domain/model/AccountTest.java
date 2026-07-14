package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.CurrencyMismatchException;
import com.ezeebit.wallet.domain.exception.InsufficientFundsException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    void creditThenDebitTracksBalance() {
        Account account = Account.opened(1L, Currency.ZAR);
        account.credit(Money.of("100.00", Currency.ZAR));
        account.debit(Money.of("30.00", Currency.ZAR));
        assertThat(account.balance().amount()).isEqualByComparingTo("70.00");
    }

    @Test
    void cannotDebitMoreThanHeld() {
        Account account = Account.opened(1L, Currency.USDT);
        account.credit(Money.of("10", Currency.USDT));
        assertThatThrownBy(() -> account.debit(Money.of("10.000001", Currency.USDT)))
                .isInstanceOf(InsufficientFundsException.class);
        // Balance is unchanged after a rejected debit.
        assertThat(account.balance().amount()).isEqualByComparingTo("10.000000");
    }

    @Test
    void cannotMoveAForeignCurrency() {
        Account account = Account.opened(1L, Currency.ZAR);
        assertThatThrownBy(() -> account.credit(Money.of("1", Currency.USDT)))
                .isInstanceOf(CurrencyMismatchException.class);
    }
}
