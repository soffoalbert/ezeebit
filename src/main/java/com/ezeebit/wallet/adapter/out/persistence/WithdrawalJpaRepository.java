package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Withdrawal;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

interface WithdrawalJpaRepository extends JpaRepository<WithdrawalEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalEntity w where w.id = :id")
    Optional<WithdrawalEntity> lockById(@Param("id") String id);

    Optional<WithdrawalEntity> findByPayoutReference(String payoutReference);

    @Query("select w from WithdrawalEntity w where w.status = :status and w.createdAt < :olderThan order by w.createdAt asc")
    List<WithdrawalEntity> findByStatusOlderThan(@Param("status") Withdrawal.Status status,
                                                 @Param("olderThan") Instant olderThan,
                                                 Pageable pageable);
}
