package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

interface OutboxJpaRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Claim due events, taking a pessimistic lock and skipping rows another relay already
     * holds. The {@code lock.timeout = -2} hint is Hibernate's SKIP LOCKED. "Due" means
     * PENDING and ready, or PROCESSING but stale (recovering a crashed relay).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("""
            select e from OutboxEventEntity e
            where (e.status = :pending and e.nextAttemptAt <= :now)
               or (e.status = :processing and e.claimedAt < :staleBefore)
            order by e.id
            """)
    List<OutboxEventEntity> findDue(@Param("now") Instant now,
                                    @Param("staleBefore") Instant staleBefore,
                                    @Param("pending") OutboxEvent.Status pending,
                                    @Param("processing") OutboxEvent.Status processing,
                                    Pageable pageable);
}
