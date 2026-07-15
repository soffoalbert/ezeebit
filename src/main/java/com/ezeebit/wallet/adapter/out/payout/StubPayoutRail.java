package com.ezeebit.wallet.adapter.out.payout;

import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase;
import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase.PayoutResult;
import com.ezeebit.wallet.application.port.out.PayoutRail;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.config.WalletProperties.PartnerStub;
import com.ezeebit.wallet.domain.exception.RailUnavailableException;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.PayoutPartner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for the real asynchronous payout rail. {@link #submit} synchronously acknowledges
 * with a reference, then — after a configurable delay — calls back with success or failure,
 * exactly as a real rail's webhook would. Behaviour is tunable per partner (via
 * {@code wallet.payout.partners.<code>.*}) with a fallback to the global knobs, so downtime and
 * failover can be demonstrated: {@code unavailable=true} makes {@link #submit} throw
 * synchronously, which the routing service treats as a signal to fail over to the next partner.
 *
 * <p>{@code @Lazy} on the use case breaks the construction-time cycle between the rail and the
 * withdrawal service (which depends on the rail).
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
    public Submission submit(PayoutPartner partner, UUID withdrawalId, Money amount, String destination) {
        PartnerStub stub = properties.payout().partners().get(partner.code());

        if (stub != null && Boolean.TRUE.equals(stub.unavailable())) {
            // Synchronous unavailability: the routing service fails over to the next partner.
            log.info("partner {} is configured unavailable; rejecting withdrawal {}", partner.code(), withdrawalId);
            throw new RailUnavailableException("partner " + partner.code() + " is unavailable");
        }

        // Deterministic reference per (partner, withdrawal): re-submitting the same withdrawal to
        // the same partner yields the same reference (idempotent on the rail side).
        String reference = "payout_" + partner.code() + "_" + withdrawalId;
        log.info("submitting withdrawal {} for {} to partner {} (ref={})",
                withdrawalId, amount, partner.code(), reference);

        long delay = stub != null && stub.settlementDelayMs() != null
                ? stub.settlementDelayMs() : properties.payout().settlementDelayMs();
        double failureRate = stub != null && stub.failureRate() != null
                ? stub.failureRate() : properties.payout().failureRate();
        boolean willFail = ThreadLocalRandom.current().nextDouble() < failureRate;

        scheduler.schedule(() -> deliverCallback(reference, willFail), Instant.now().plusMillis(delay));
        return new Submission(reference);
    }

    private void deliverCallback(String reference, boolean willFail) {
        try {
            PayoutResult result = willFail
                    ? new PayoutResult(reference, PayoutResult.Outcome.FAILED, "rail rejected the payout")
                    : new PayoutResult(reference, PayoutResult.Outcome.SUCCEEDED, null);
            payoutResultHandler.handle(result);
        } catch (RuntimeException e) {
            // A real rail retries until acknowledged; here we log and let the recovery sweeper
            // reconcile. (The callback only arrives after the settlement delay, by which point the
            // SUBMITTED state and its reference are committed.)
            log.warn("payout callback for {} could not be applied: {}", reference, e.getMessage());
        }
    }
}
