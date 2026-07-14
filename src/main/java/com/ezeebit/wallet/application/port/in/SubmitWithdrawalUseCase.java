package com.ezeebit.wallet.application.port.in;

import java.util.UUID;

/**
 * Submits a PENDING withdrawal to the payout rail and marks it SUBMITTED. Invoked
 * asynchronously after the hold commits, and by the recovery sweeper for withdrawals
 * that were held but never submitted (e.g. a crash between commit and submission).
 */
public interface SubmitWithdrawalUseCase {
    void submit(UUID withdrawalId);
}
