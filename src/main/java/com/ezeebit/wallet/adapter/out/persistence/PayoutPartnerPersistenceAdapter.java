package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.PayoutPartnerRepository;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.PayoutPartner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
class PayoutPartnerPersistenceAdapter implements PayoutPartnerRepository {

    private final PayoutPartnerJpaRepository jpa;

    PayoutPartnerPersistenceAdapter(PayoutPartnerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<PayoutPartner> findRoutes(String country, Currency currency) {
        return jpa.findRoutes(country, currency).stream().map(PayoutPartnerEntity::toDomain).toList();
    }

    @Override
    public Optional<PayoutPartner> findByCode(String code) {
        return jpa.findByCode(code).map(PayoutPartnerEntity::toDomain);
    }

    @Override
    public List<PayoutPartner> findAll() {
        return jpa.findAllByOrderByCurrencyAscPriorityAsc().stream()
                .map(PayoutPartnerEntity::toDomain).toList();
    }

    @Override
    @Transactional
    public boolean setHealthy(String code, boolean healthy) {
        return jpa.updateHealthy(code, healthy) > 0;
    }
}
