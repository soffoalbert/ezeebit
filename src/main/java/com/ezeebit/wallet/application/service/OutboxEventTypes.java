package com.ezeebit.wallet.application.service;

/** Well-known outbox aggregate and event type identifiers. */
public final class OutboxEventTypes {

    public static final String AGG_WITHDRAWAL = "WITHDRAWAL";
    public static final String WITHDRAWAL_SUBMISSION_REQUESTED = "WITHDRAWAL_SUBMISSION_REQUESTED";

    private OutboxEventTypes() {}
}
