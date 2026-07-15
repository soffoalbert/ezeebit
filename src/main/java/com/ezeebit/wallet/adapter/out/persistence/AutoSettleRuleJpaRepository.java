package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface AutoSettleRuleJpaRepository extends JpaRepository<AutoSettleRuleEntity, Long> {

    Optional<AutoSettleRuleEntity> findByMerchantIdAndSourceCurrency(long merchantId, Currency sourceCurrency);

    List<AutoSettleRuleEntity> findByMerchantIdOrderBySourceCurrency(long merchantId);
}
