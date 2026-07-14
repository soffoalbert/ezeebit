package com.ezeebit.wallet.adapter.out.payout;

import com.ezeebit.wallet.adapter.out.payout.PayoutSubmissionEventAdapter.WithdrawalSubmissionRequested;
import com.ezeebit.wallet.application.port.in.SubmitWithdrawalUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Submits a held withdrawal to the payout rail after the holding transaction commits.
 * Runs asynchronously so the request thread is not blocked on the rail. If submission
 * fails here, the withdrawal remains PENDING and the recovery sweeper retries it.
 */
@Component
class WithdrawalSubmissionListener {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalSubmissionListener.class);

    private final SubmitWithdrawalUseCase submitWithdrawal;

    WithdrawalSubmissionListener(SubmitWithdrawalUseCase submitWithdrawal) {
        this.submitWithdrawal = submitWithdrawal;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onSubmissionRequested(WithdrawalSubmissionRequested event) {
        try {
            submitWithdrawal.submit(event.withdrawalId());
        } catch (RuntimeException e) {
            log.warn("submission of withdrawal {} failed post-commit; sweeper will retry: {}",
                    event.withdrawalId(), e.getMessage());
        }
    }
}
