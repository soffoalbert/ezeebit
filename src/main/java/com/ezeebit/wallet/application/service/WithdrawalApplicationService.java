package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.SubmitWithdrawalUseCase;
import com.ezeebit.wallet.application.port.out.PayoutRail;
import com.ezeebit.wallet.application.port.out.PayoutSubmissionScheduler;
import com.ezeebit.wallet.application.port.out.WithdrawalRepository;
import com.ezeebit.wallet.application.service.support.RequestHash;
import com.ezeebit.wallet.domain.exception.WithdrawalNotFoundException;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Task 3 — withdraw funds out of the platform.
 *
 * <p>Safety properties:
 * <ul>
 *   <li><b>Never overdraw:</b> funds are held (debited) inside the request transaction,
 *       before the rail is called, so concurrent withdrawals cannot double-spend.</li>
 *   <li><b>Never pay out twice:</b> the request is idempotent on the client key, and the
 *       withdrawal state machine makes submission and settlement single-shot.</li>
 *   <li><b>Balance always right, even on failure:</b> a failed payout posts a compensating
 *       RELEASE credit; a succeeded one posts a zero-amount SETTLE marker for the trail.</li>
 * </ul>
 */
@Service
public class WithdrawalApplicationService
        implements RequestWithdrawalUseCase, GetWithdrawalUseCase,
                   HandlePayoutResultUseCase, SubmitWithdrawalUseCase {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalApplicationService.class);
    private static final String ENDPOINT = "withdrawal";

    private final WithdrawalRepository withdrawals;
    private final LedgerPostingService posting;
    private final PayoutRail rail;
    private final PayoutSubmissionScheduler submissionScheduler;
    private final IdempotencyGuard idempotency;
    private final Clock clock;

    public WithdrawalApplicationService(WithdrawalRepository withdrawals, LedgerPostingService posting,
                                        PayoutRail rail, PayoutSubmissionScheduler submissionScheduler,
                                        IdempotencyGuard idempotency, Clock clock) {
        this.withdrawals = withdrawals;
        this.posting = posting;
        this.rail = rail;
        this.submissionScheduler = submissionScheduler;
        this.idempotency = idempotency;
        this.clock = clock;
    }

    @Override
    @Transactional
    public WithdrawalView request(RequestWithdrawalCommand command) {
        Money amount = Money.of(command.amount(), command.currency());
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("withdrawal amount must be positive");
        }
        if (command.destination() == null || command.destination().isBlank()) {
            throw new IllegalArgumentException("withdrawal destination is required");
        }
        String hash = RequestHash.of(command.merchantId(), command.currency(),
                amount.amount(), command.destination());

        return idempotency.execute(command.merchantId(), ENDPOINT, command.idempotencyKey(), hash,
                WithdrawalView.class, () -> {
                    Instant now = clock.instant();
                    Withdrawal withdrawal = Withdrawal.requested(command.merchantId(),
                            command.idempotencyKey(), amount, command.destination(), now);

                    // Hold the funds before the rail is ever called.
                    posting.post(command.merchantId(), LedgerEntryType.WITHDRAWAL_HOLD, amount,
                            withdrawal.operationId(), "withdrawal " + withdrawal.id());
                    withdrawals.save(withdrawal);

                    // Submit to the rail only after this transaction commits.
                    submissionScheduler.schedule(withdrawal.id());
                    return toView(withdrawal);
                });
    }

    @Override
    @Transactional
    public void submit(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawals.lockForUpdate(withdrawalId)
                .orElseThrow(() -> new WithdrawalNotFoundException(withdrawalId.toString()));
        if (withdrawal.status() != Withdrawal.Status.PENDING) {
            return;   // already submitted or resolved — nothing to do
        }
        // The rail call is idempotent on the withdrawal id, so re-submitting is safe.
        PayoutRail.Submission submission = rail.submit(withdrawal.id(), withdrawal.amount(),
                withdrawal.destination());
        withdrawal.markSubmitted(submission.payoutReference(), clock.instant());
        withdrawals.save(withdrawal);
    }

    @Override
    @Transactional
    public void handle(PayoutResult result) {
        Withdrawal withdrawal = withdrawals.findByPayoutReference(result.payoutReference())
                .flatMap(w -> withdrawals.lockForUpdate(w.id()))
                .orElseThrow(() -> new WithdrawalNotFoundException(
                        "payout-ref " + result.payoutReference()));

        Instant now = clock.instant();
        if (result.outcome() == PayoutResult.Outcome.SUCCEEDED) {
            if (withdrawal.markCompleted(now)) {
                // Funds already left at hold time; record a marker for the audit trail.
                posting.post(withdrawal.merchantId(), LedgerEntryType.WITHDRAWAL_SETTLE,
                        Money.zero(withdrawal.amount().currency()), withdrawal.operationId(),
                        "withdrawal " + withdrawal.id());
                withdrawals.save(withdrawal);
            }
        } else {
            if (withdrawal.markFailed(result.reason(), now)) {
                // Give the held funds back.
                posting.post(withdrawal.merchantId(), LedgerEntryType.WITHDRAWAL_RELEASE,
                        withdrawal.amount(), withdrawal.operationId(),
                        "withdrawal " + withdrawal.id());
                withdrawals.save(withdrawal);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public WithdrawalView get(long merchantId, String withdrawalId) {
        Withdrawal withdrawal = withdrawals.find(parseId(withdrawalId))
                .filter(w -> w.merchantId() == merchantId)
                .orElseThrow(() -> new WithdrawalNotFoundException(withdrawalId));
        return toView(withdrawal);
    }

    private WithdrawalView toView(Withdrawal w) {
        return new WithdrawalView(w.id().toString(), w.merchantId(), w.amount().currency(),
                w.amount().amount(), w.status().name(), w.payoutReference(), w.failureReason(),
                w.createdAt(), w.updatedAt());
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new WithdrawalNotFoundException(raw);
        }
    }
}
