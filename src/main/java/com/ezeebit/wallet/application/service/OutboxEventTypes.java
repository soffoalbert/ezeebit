package com.ezeebit.wallet.application.service;

/** Well-known outbox aggregate and event type identifiers. */
public final class OutboxEventTypes {

    public static final String AGG_WITHDRAWAL = "WITHDRAWAL";
    public static final String WITHDRAWAL_SUBMISSION_REQUESTED = "WITHDRAWAL_SUBMISSION_REQUESTED";

    public static final String AGG_INCOMING_PAYMENT = "INCOMING_PAYMENT";
    public static final String INCOMING_PAYMENT_CONFIRMED = "INCOMING_PAYMENT_CONFIRMED";

    private OutboxEventTypes() {}
}
