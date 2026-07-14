package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.LedgerRepository;
import com.ezeebit.wallet.domain.model.LedgerEntry;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
class LedgerPersistenceAdapter implements LedgerRepository {

    private final LedgerJpaRepository jpa;

    LedgerPersistenceAdapter(LedgerJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public LedgerEntry append(LedgerEntry entry) {
        return jpa.save(LedgerEntryEntity.fromDomain(entry)).toDomain();
    }

    @Override
    public List<LedgerEntry> findByAccount(long accountId, int limit, int offset) {
        int page = limit <= 0 ? 0 : offset / limit;
        int size = limit <= 0 ? 50 : limit;
        return jpa.findByAccount(accountId, PageRequest.of(page, size)).stream()
                .map(LedgerEntryEntity::toDomain)
                .toList();
    }

    @Override
    public BigDecimal sumByAccount(long accountId) {
        BigDecimal sum = jpa.sumByAccount(accountId);
        return sum == null ? BigDecimal.ZERO : sum;
    }
}
