package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface WithdrawalLimitJpaRepository extends JpaRepository<WithdrawalLimitEntity, Long> {
    Optional<WithdrawalLimitEntity> findByMerchantIdAndCurrency(long merchantId, Currency currency);
}
