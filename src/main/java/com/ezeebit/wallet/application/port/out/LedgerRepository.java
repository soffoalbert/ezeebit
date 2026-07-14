package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.LedgerEntry;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerRepository {

    LedgerEntry append(LedgerEntry entry);

    /** Entries for one account, newest first, paginated. */
    List<LedgerEntry> findByAccount(long accountId, int limit, int offset);

    /** Sum of all signed amounts for an account — used to assert the ledger invariant. */
    BigDecimal sumByAccount(long accountId);
}
