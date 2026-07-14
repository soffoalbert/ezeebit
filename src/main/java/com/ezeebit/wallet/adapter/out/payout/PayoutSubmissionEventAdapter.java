package com.ezeebit.wallet.adapter.out.payout;

import com.ezeebit.wallet.application.port.out.PayoutSubmissionScheduler;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Schedules payout submission by publishing an application event. A listener picks it up
 * only after the holding transaction commits (see {@link WithdrawalSubmissionListener}),
 * so we never call the rail for a hold that might still roll back.
 */
@Component
class PayoutSubmissionEventAdapter implements PayoutSubmissionScheduler {

    private final ApplicationEventPublisher publisher;

    PayoutSubmissionEventAdapter(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void schedule(UUID withdrawalId) {
        publisher.publishEvent(new WithdrawalSubmissionRequested(withdrawalId));
    }

    record WithdrawalSubmissionRequested(UUID withdrawalId) {}
}
