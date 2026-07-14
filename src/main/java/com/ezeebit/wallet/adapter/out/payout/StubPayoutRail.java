package com.ezeebit.wallet.adapter.out.payout;

import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase;
import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase.PayoutResult;
import com.ezeebit.wallet.application.port.out.PayoutRail;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.model.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for the real asynchronous payout rail. {@link #submit} synchronously
 * acknowledges with a reference, then — after a configurable delay — calls back with
 * success or failure, exactly as a real rail's webhook would. The callback is delivered
 * by invoking the {@link HandlePayoutResultUseCase}; the same use case also backs the
 * public HTTP callback endpoint, so the two paths are interchangeable.
 *
 * <p>{@code @Lazy} on the use case breaks the construction-time cycle between the rail
 * and the withdrawal service (which depends on the rail).
 */
@Component
class StubPayoutRail implements PayoutRail {

    private static final Logger log = LoggerFactory.getLogger(StubPayoutRail.class);

    private final HandlePayoutResultUseCase payoutResultHandler;
    private final TaskScheduler scheduler;
    private final WalletProperties properties;

    StubPayoutRail(@Lazy HandlePayoutResultUseCase payoutResultHandler,
                   TaskScheduler scheduler, WalletProperties properties) {
        this.payoutResultHandler = payoutResultHandler;
        this.scheduler = scheduler;
        this.properties = properties;
    }

    @Override
    public Submission submit(UUID withdrawalId, Money amount, String destination) {
        // Deterministic reference derived from the withdrawal id, so re-submitting the
        // same withdrawal yields the same reference (idempotent on the rail side).
        String reference = "payout_" + withdrawalId;
        log.info("submitting withdrawal {} for {} to rail (ref={})", withdrawalId, amount, reference);

        long delay = properties.payout().settlementDelayMs();
        boolean willFail = ThreadLocalRandom.current().nextDouble() < properties.payout().failureRate();
        scheduler.schedule(() -> deliverCallback(reference, willFail),
                Instant.now().plusMillis(delay));
        return new Submission(reference);
    }

    private void deliverCallback(String reference, boolean willFail) {
        try {
            PayoutResult result = willFail
                    ? new PayoutResult(reference, PayoutResult.Outcome.FAILED, "rail rejected the payout")
                    : new PayoutResult(reference, PayoutResult.Outcome.SUCCEEDED, null);
            payoutResultHandler.handle(result);
        } catch (RuntimeException e) {
            // A real rail retries until acknowledged; here we log and let the recovery
            // sweeper reconcile. (The callback only arrives after the settlement delay, by
            // which point the SUBMITTED state and its reference are committed.)
            log.warn("payout callback for {} could not be applied: {}", reference, e.getMessage());
        }
    }
}
