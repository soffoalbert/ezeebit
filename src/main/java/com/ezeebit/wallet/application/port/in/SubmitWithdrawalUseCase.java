package com.ezeebit.wallet.application.port.in;

import java.util.UUID;

/**
 * Submits a PENDING withdrawal to the payout rail and marks it SUBMITTED. Invoked
 * asynchronously after the hold commits, and by the recovery sweeper for withdrawals
 * that were held but never submitted (e.g. a crash between commit and submission).
 */
public interface SubmitWithdrawalUseCase {
    void submit(UUID withdrawalId);

    /**
     * Terminally fail a payout that has been held but never submitted for longer than the
     * configured deadline, releasing the held funds. The safety net that guarantees money is
     * never stuck when no partner can accept a withdrawal.
     */
    void failExpired(UUID withdrawalId);
}
