package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.WithdrawalRepository;
import com.ezeebit.wallet.domain.model.Withdrawal;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
class WithdrawalPersistenceAdapter implements WithdrawalRepository {

    private final WithdrawalJpaRepository jpa;

    WithdrawalPersistenceAdapter(WithdrawalJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Withdrawal save(Withdrawal withdrawal) {
        return jpa.save(WithdrawalEntity.fromDomain(withdrawal)).toDomain();
    }

    @Override
    public Optional<Withdrawal> find(UUID id) {
        return jpa.findById(id.toString()).map(WithdrawalEntity::toDomain);
    }

    @Override
    public Optional<Withdrawal> lockForUpdate(UUID id) {
        return jpa.lockById(id.toString()).map(WithdrawalEntity::toDomain);
    }

    @Override
    public Optional<Withdrawal> findByPayoutReference(String payoutReference) {
        return jpa.findByPayoutReference(payoutReference).map(WithdrawalEntity::toDomain);
    }

    @Override
    public List<Withdrawal> findStalePending(Instant olderThan, int limit) {
        return jpa.findByStatusOlderThan(Withdrawal.Status.PENDING, olderThan, PageRequest.of(0, limit))
                .stream()
                .map(WithdrawalEntity::toDomain)
                .toList();
    }
}
