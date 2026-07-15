package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.AutoSettleRuleRepository;
import com.ezeebit.wallet.domain.model.AutoSettleRule;
import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class AutoSettleRulePersistenceAdapter implements AutoSettleRuleRepository {

    private final AutoSettleRuleJpaRepository jpa;

    AutoSettleRulePersistenceAdapter(AutoSettleRuleJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<AutoSettleRule> find(long merchantId, Currency sourceCurrency) {
        return jpa.findByMerchantIdAndSourceCurrency(merchantId, sourceCurrency)
                .map(AutoSettleRuleEntity::toDomain);
    }

    @Override
    public List<AutoSettleRule> findAllForMerchant(long merchantId) {
        return jpa.findByMerchantIdOrderBySourceCurrency(merchantId).stream()
                .map(AutoSettleRuleEntity::toDomain)
                .toList();
    }

    @Override
    public AutoSettleRule upsert(AutoSettleRule rule) {
        AutoSettleRuleEntity entity = jpa
                .findByMerchantIdAndSourceCurrency(rule.merchantId(), rule.sourceCurrency())
                .map(existing -> {
                    existing.applyUpsert(rule);
                    return existing;
                })
                .orElseGet(() -> AutoSettleRuleEntity.fromDomain(rule));
        return jpa.save(entity).toDomain();
    }
}
