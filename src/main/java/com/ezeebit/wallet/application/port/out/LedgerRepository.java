package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerRepository {

    LedgerEntry append(LedgerEntry entry);

    /**
     * Entries for one account, newest first. When {@code beforeId} is non-null, only
     * entries with a smaller id are returned (keyset/cursor pagination).
     */
    List<LedgerEntry> findByAccount(long accountId, Long beforeId, int limit);

    /** Sum of all signed amounts for an account — used to assert the ledger invariant. */
    BigDecimal sumByAccount(long accountId);
}
