package com.ezeebit.wallet.application.port.out;

import java.util.UUID;

/**
 * Defers submission of a freshly-held withdrawal to the payout rail until after the
 * holding transaction has committed. Keeps the external rail call out of the DB
 * transaction (so the account row lock is not held across a network call) and
 * guarantees we only ever submit a withdrawal whose funds are already committed-held.
 */
public interface PayoutSubmissionScheduler {
    void schedule(UUID withdrawalId);
}
