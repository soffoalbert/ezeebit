package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.AccountRepository;
import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class AccountPersistenceAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;

    AccountPersistenceAdapter(AccountJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<Account> lockForUpdate(long merchantId, Currency currency) {
        return jpa.lockByMerchantAndCurrency(merchantId, currency).map(AccountEntity::toDomain);
    }

    @Override
    public Optional<Account> find(long merchantId, Currency currency) {
        return jpa.findByMerchantIdAndCurrency(merchantId, currency).map(AccountEntity::toDomain);
    }

    @Override
    public List<Account> findAllForMerchant(long merchantId) {
        return jpa.findByMerchantIdOrderByCurrency(merchantId).stream()
                .map(AccountEntity::toDomain)
                .toList();
    }

    @Override
    public Account save(Account account) {
        return jpa.saveAndFlush(AccountEntity.fromDomain(account)).toDomain();
    }
}
