package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Money;

import java.util.UUID;

/**
 * The external, asynchronous payout rail. Assumed to already exist. We ask it to pay
 * out and it later reports success or failure via a callback (see the payout-callback
 * web adapter). The call is idempotent on {@code withdrawalId}: submitting the same
 * withdrawal twice must not pay out twice.
 */
public interface PayoutRail {

    Submission submit(UUID withdrawalId, Money amount, String destination);

    /** Synchronous acknowledgement that the request was accepted for processing. */
    record Submission(String payoutReference) {}
}
