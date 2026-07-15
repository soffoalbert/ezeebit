package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.SubmitWithdrawalUseCase;
import com.ezeebit.wallet.application.port.out.MerchantDirectory;
import com.ezeebit.wallet.application.port.out.OutboxRepository;
import com.ezeebit.wallet.application.port.out.PayoutRail;
import com.ezeebit.wallet.application.port.out.WithdrawalLimitRepository;
import com.ezeebit.wallet.application.port.out.WithdrawalRepository;
import com.ezeebit.wallet.application.service.support.PayoutDestinationValidator;
import com.ezeebit.wallet.application.service.support.RequestHash;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.exception.NoPayoutRouteException;
import com.ezeebit.wallet.domain.exception.RailUnavailableException;
import com.ezeebit.wallet.domain.exception.WithdrawalLimitExceededException;
import com.ezeebit.wallet.domain.exception.WithdrawalNotFoundException;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.OutboxEvent;
import com.ezeebit.wallet.domain.model.PayoutPartner;
import com.ezeebit.wallet.domain.model.RoutingPlan;
import com.ezeebit.wallet.domain.model.Withdrawal;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
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
    private final OutboxRepository outbox;
    private final WithdrawalLimitRepository limits;
    private final PayoutDestinationValidator destinationValidator;
    private final PayoutRoutingService routing;
    private final MerchantDirectory merchants;
    private final IdempotencyGuard idempotency;
    private final MeterRegistry meters;
    private final WalletProperties properties;
    private final Clock clock;

    public WithdrawalApplicationService(WithdrawalRepository withdrawals, LedgerPostingService posting,
                                        PayoutRail rail, OutboxRepository outbox,
                                        WithdrawalLimitRepository limits,
                                        PayoutDestinationValidator destinationValidator,
                                        PayoutRoutingService routing, MerchantDirectory merchants,
                                        IdempotencyGuard idempotency, MeterRegistry meters,
                                        WalletProperties properties, Clock clock) {
        this.withdrawals = withdrawals;
        this.posting = posting;
        this.rail = rail;
        this.outbox = outbox;
        this.limits = limits;
        this.destinationValidator = destinationValidator;
        this.routing = routing;
        this.merchants = merchants;
        this.idempotency = idempotency;
        this.meters = meters;
        this.properties = properties;
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
        destinationValidator.validate(command.currency(), command.destination());
        enforceLimit(command.merchantId(), amount);

        // Reject a structurally impossible payout (no partner for this country+currency, or the
        // amount exceeds every partner's limit) BEFORE holding any funds — a 422, not a held hold.
        String country = merchants.countryOf(command.merchantId());
        if (!routing.structuralRouteExists(country, amount)) {
            throw new NoPayoutRouteException(country, command.currency());
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

                    // Durably record the intent to submit, in the same transaction as the hold.
                    // The outbox relay dispatches it after commit; a crash cannot lose it.
                    outbox.append(OutboxEvent.create(
                            OutboxEventTypes.AGG_WITHDRAWAL, withdrawal.id().toString(),
                            OutboxEventTypes.WITHDRAWAL_SUBMISSION_REQUESTED,
                            "{\"withdrawalId\":\"" + withdrawal.id() + "\"}", now));
                    return toView(withdrawal);
                });
    }

    private void enforceLimit(long merchantId, Money amount) {
        Optional<BigDecimal> max = limits.maxPerWithdrawal(merchantId, amount.currency())
                .or(() -> Optional.ofNullable(
                        properties.withdrawal().maxPerCurrency().get(amount.currency().name())));
        if (max.isPresent() && amount.amount().compareTo(max.get()) > 0) {
            throw new WithdrawalLimitExceededException(amount, max.get());
        }
    }

    @Override
    @Transactional
    public void submit(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawals.lockForUpdate(withdrawalId)
                .orElseThrow(() -> new WithdrawalNotFoundException(withdrawalId.toString()));
        if (withdrawal.status() != Withdrawal.Status.PENDING) {
            return;   // already submitted or resolved — nothing to do
        }

        String country = merchants.countryOf(withdrawal.merchantId());
        RoutingPlan plan = routing.plan(country, withdrawal.amount());

        if (!plan.hasEligible()) {
            if (plan.isTransientlyUnroutable()) {
                // Every candidate is merely unhealthy: keep the funds held and retry later.
                throw new RailUnavailableException(
                        "all payout partners for " + withdrawal.amount().currency() + " are unhealthy");
            }
            // No usable route (config changed since the request): terminal — release the funds.
            failAndRelease(withdrawal, "no payout route available", clock.instant());
            return;
        }

        // Try eligible partners in priority order, failing over on a synchronous unavailability.
        for (PayoutPartner partner : plan.eligible()) {
            try {
                // The rail call is idempotent on the withdrawal id, so re-submitting is safe.
                PayoutRail.Submission submission = rail.submit(partner, withdrawal.id(),
                        withdrawal.amount(), withdrawal.destination());
                withdrawal.markSubmitted(submission.payoutReference(), partner.code(), clock.instant());
                withdrawals.save(withdrawal);
                meters.counter("wallet.payout.routed", "partner", partner.code()).increment();
                return;
            } catch (RailUnavailableException e) {
                meters.counter("wallet.payout.failover", "from", partner.code()).increment();
                log.warn("payout partner {} unavailable for withdrawal {}, failing over: {}",
                        partner.code(), withdrawal.id(), e.getMessage());
            }
        }

        // All eligible partners rejected synchronously: stays PENDING, retried by outbox/sweeper.
        throw new RailUnavailableException(
                "all eligible payout partners rejected withdrawal " + withdrawal.id());
    }

    @Override
    @Transactional
    public void failExpired(UUID withdrawalId) {
        Withdrawal withdrawal = withdrawals.lockForUpdate(withdrawalId)
                .orElseThrow(() -> new WithdrawalNotFoundException(withdrawalId.toString()));
        if (withdrawal.status() != Withdrawal.Status.PENDING) {
            return;   // it got submitted/resolved in the meantime
        }
        failAndRelease(withdrawal, "payout deadline exceeded; no partner accepted it", clock.instant());
    }

    private void failAndRelease(Withdrawal withdrawal, String reason, Instant now) {
        if (withdrawal.markFailed(reason, now)) {
            posting.post(withdrawal.merchantId(), LedgerEntryType.WITHDRAWAL_RELEASE,
                    withdrawal.amount(), withdrawal.operationId(), "withdrawal " + withdrawal.id());
            withdrawals.save(withdrawal);
        }
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
                w.amount().amount(), w.status().name(), w.payoutReference(), w.partnerCode(),
                w.failureReason(), w.createdAt(), w.updatedAt());
    }

    private UUID parseId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new WithdrawalNotFoundException(raw);
        }
    }
}
