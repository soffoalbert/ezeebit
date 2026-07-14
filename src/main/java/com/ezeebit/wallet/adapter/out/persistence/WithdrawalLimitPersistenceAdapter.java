package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.WithdrawalLimitRepository;
import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
class WithdrawalLimitPersistenceAdapter implements WithdrawalLimitRepository {

    private final WithdrawalLimitJpaRepository jpa;

    WithdrawalLimitPersistenceAdapter(WithdrawalLimitJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<BigDecimal> maxPerWithdrawal(long merchantId, Currency currency) {
        return jpa.findByMerchantIdAndCurrency(merchantId, currency)
                .map(WithdrawalLimitEntity::getMaxPerWithdrawal);
    }
}
