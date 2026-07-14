package com.ezeebit.wallet.domain.model;

/**
 * The reasons a ledger entry can be written. The {@link #signum} says whether the
 * entry increases (+1) or decreases (-1) the account balance. A {@code 0} entry is
 * a pure audit marker that records that something happened without moving money.
 */
public enum LedgerEntryType {
    DEPOSIT(+1),
    CONVERSION_IN(+1),
    CONVERSION_OUT(-1),
    WITHDRAWAL_HOLD(-1),      // funds leave the spendable balance when a payout is requested
    WITHDRAWAL_SETTLE(0),     // marker: the held payout completed on the rail
    WITHDRAWAL_RELEASE(+1);   // compensating credit when a payout ultimately fails

    private final int signum;

    LedgerEntryType(int signum) {
        this.signum = signum;
    }

    public int signum() {
        return signum;
    }
}
