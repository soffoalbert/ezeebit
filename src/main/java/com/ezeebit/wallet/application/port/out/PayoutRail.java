package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.PayoutPartner;

import java.util.UUID;

/**
 * The external, asynchronous payout rail. Assumed to already exist. We ask a chosen
 * {@link PayoutPartner} to pay out and it later reports success or failure via a callback
 * (see the payout-callback web adapter). The call is idempotent on {@code withdrawalId}:
 * submitting the same withdrawal twice must not pay out twice. It may throw
 * {@link com.ezeebit.wallet.domain.exception.RailUnavailableException} to signal the partner
 * is temporarily unavailable, which the routing service treats as a trigger to fail over.
 */
public interface PayoutRail {

    Submission submit(PayoutPartner partner, UUID withdrawalId, Money amount, String destination);

    /** Synchronous acknowledgement that the request was accepted for processing. */
    record Submission(String payoutReference) {}
}
