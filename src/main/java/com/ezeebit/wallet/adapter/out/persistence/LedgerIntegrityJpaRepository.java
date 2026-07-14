package com.ezeebit.wallet.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

interface LedgerIntegrityJpaRepository extends JpaRepository<AccountEntity, Long> {

    /** Accounts whose cached balance disagrees with the sum of their ledger entries. */
    @Query(value = """
            SELECT a.id AS accountId, a.balance AS balance,
                   COALESCE((SELECT SUM(le.amount) FROM ledger_entry le WHERE le.account_id = a.id), 0) AS ledgerSum
            FROM account a
            WHERE a.balance <> COALESCE(
                    (SELECT SUM(le.amount) FROM ledger_entry le WHERE le.account_id = a.id), 0)
            LIMIT :limit
            """, nativeQuery = true)
    List<BalanceDriftRow> findDrift(@Param("limit") int limit);

    interface BalanceDriftRow {
        long getAccountId();
        BigDecimal getBalance();
        BigDecimal getLedgerSum();
    }
}
