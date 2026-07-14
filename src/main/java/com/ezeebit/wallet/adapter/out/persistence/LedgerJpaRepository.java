package com.ezeebit.wallet.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

interface LedgerJpaRepository extends JpaRepository<LedgerEntryEntity, Long> {

    @Query("select e from LedgerEntryEntity e where e.accountId = :accountId "
            + "and (:beforeId is null or e.id < :beforeId) order by e.id desc")
    List<LedgerEntryEntity> findByAccount(@Param("accountId") long accountId,
                                          @Param("beforeId") Long beforeId, Pageable pageable);

    @Query("select coalesce(sum(e.amount), 0) from LedgerEntryEntity e where e.accountId = :accountId")
    BigDecimal sumByAccount(@Param("accountId") long accountId);
}
