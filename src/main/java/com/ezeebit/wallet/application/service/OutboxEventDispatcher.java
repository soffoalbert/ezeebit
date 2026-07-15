package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.ExecuteAutoSettleUseCase;
import com.ezeebit.wallet.application.port.in.SubmitWithdrawalUseCase;
import com.ezeebit.wallet.domain.model.OutboxEvent;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Routes a claimed outbox event to the use case that performs its side effect. Each
 * target use case is idempotent, so re-dispatching an event (after a crash or retry)
 * is safe.
 */
@Component
public class OutboxEventDispatcher {

    private final SubmitWithdrawalUseCase submitWithdrawal;
    private final ExecuteAutoSettleUseCase executeAutoSettle;

    public OutboxEventDispatcher(SubmitWithdrawalUseCase submitWithdrawal,
                                 ExecuteAutoSettleUseCase executeAutoSettle) {
        this.submitWithdrawal = submitWithdrawal;
        this.executeAutoSettle = executeAutoSettle;
    }

    public void dispatch(OutboxEvent event) {
        switch (event.eventType()) {
            case OutboxEventTypes.WITHDRAWAL_SUBMISSION_REQUESTED ->
                    submitWithdrawal.submit(UUID.fromString(event.aggregateId()));
            case OutboxEventTypes.INCOMING_PAYMENT_CONFIRMED ->
                    executeAutoSettle.settle(UUID.fromString(event.aggregateId()));
            default ->
                    throw new IllegalStateException("no handler for outbox event type: " + event.eventType());
        }
    }
}
