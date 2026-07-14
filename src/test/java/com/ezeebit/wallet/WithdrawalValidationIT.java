package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.RequestWithdrawalCommand;
import com.ezeebit.wallet.domain.exception.InvalidDestinationException;
import com.ezeebit.wallet.domain.exception.WithdrawalLimitExceededException;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithdrawalValidationIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;   // seeded with a 5,000 USDT per-withdrawal limit

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    RequestWithdrawalUseCase withdraw;

    @Test
    void withdrawalOverTheLimitIsRejectedAndHoldsNothing() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("10000.000000"),
                "seed-lim", "seed"));

        assertThatThrownBy(() -> withdraw.request(new RequestWithdrawalCommand(
                MERCHANT, Currency.USDT, new BigDecimal("6000.000000"),
                "{\"address\":\"chain-address-000\"}", "wd-over")))
                .isInstanceOf(WithdrawalLimitExceededException.class);

        // Nothing was held; the balance is untouched.
        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("10000.000000");
        assertLedgerInvariantHolds();
    }

    @Test
    void malformedDestinationIsRejected() {
        deposit.deposit(new DepositCommand(MERCHANT, Currency.USDT, new BigDecimal("100.000000"),
                "seed-dest", "seed"));

        // Stablecoin payout with no blockchain address.
        assertThatThrownBy(() -> withdraw.request(new RequestWithdrawalCommand(
                MERCHANT, Currency.USDT, new BigDecimal("10.000000"),
                "{\"bankCode\":\"ABC\"}", "wd-baddest")))
                .isInstanceOf(InvalidDestinationException.class);

        assertThat(balanceOf(MERCHANT, "USDT")).isEqualByComparingTo("100.000000");
        assertLedgerInvariantHolds();
    }
}
