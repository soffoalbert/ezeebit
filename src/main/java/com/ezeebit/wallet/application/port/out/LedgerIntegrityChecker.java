package com.ezeebit.wallet.application.port.out;

import java.math.BigDecimal;
import java.util.List;

/**
 * Detects drift between an account's cached balance and the sum of its ledger entries —
 * the core invariant of the wallet. Used by a monitor to alert if it is ever violated.
 */
public interface LedgerIntegrityChecker {

    List<BalanceDrift> findDrift(int limit);

    record BalanceDrift(long accountId, BigDecimal balance, BigDecimal ledgerSum) {
        public BigDecimal difference() {
            return balance.subtract(ledgerSum);
        }
    }
}
