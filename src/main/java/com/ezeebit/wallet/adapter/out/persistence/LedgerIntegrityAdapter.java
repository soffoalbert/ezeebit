package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.LedgerIntegrityChecker;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class LedgerIntegrityAdapter implements LedgerIntegrityChecker {

    private final LedgerIntegrityJpaRepository jpa;

    LedgerIntegrityAdapter(LedgerIntegrityJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<BalanceDrift> findDrift(int limit) {
        return jpa.findDrift(limit).stream()
                .map(r -> new BalanceDrift(r.getAccountId(), r.getBalance(), r.getLedgerSum()))
                .toList();
    }
}
