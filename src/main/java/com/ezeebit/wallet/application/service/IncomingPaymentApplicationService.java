package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.GetIncomingPaymentsUseCase;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase;
import com.ezeebit.wallet.application.port.out.IncomingPaymentRepository;
import com.ezeebit.wallet.application.port.out.OutboxRepository;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.exception.ConcurrentRequestException;
import com.ezeebit.wallet.domain.model.IncomingPayment;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.OutboxEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Task 4 — accept an incoming crypto payment through its pending → available lifecycle.
 *
 * <p>The confirmation service notifies us (at-least-once, possibly out of order) as a payment
 * gains confirmations. Each notification locks or inserts the payment row keyed by
 * {@code (txHash, outputIndex)}:
 * <ul>
 *   <li>A first sighting inserts a PENDING row — visible on balances as {@code pendingIncoming}
 *       but not spendable. The unique constraint arbitrates the first-insert race.</li>
 *   <li>Further notifications fold in the monotonic-max confirmation count. A duplicate or
 *       regressed count is a silent no-op; a notification whose immutable facts disagree with
 *       the recorded row is a 409 conflict.</li>
 *   <li>When the count reaches the threshold, the payment confirms exactly once: a single
 *       {@code INCOMING_CREDIT} ledger entry makes the funds spendable, and an outbox event
 *       is appended in the same transaction to drive auto-settle (Task 5).</li>
 * </ul>
 */
@Service
public class IncomingPaymentApplicationService
        implements NotifyIncomingPaymentUseCase, GetIncomingPaymentsUseCase {

    private static final Logger audit = LoggerFactory.getLogger("wallet.audit");

    private final IncomingPaymentRepository incomingPayments;
    private final LedgerPostingService posting;
    private final OutboxRepository outbox;
    private final WalletProperties properties;
    private final MeterRegistry meters;
    private final Clock clock;

    public IncomingPaymentApplicationService(IncomingPaymentRepository incomingPayments,
                                             LedgerPostingService posting, OutboxRepository outbox,
                                             WalletProperties properties, MeterRegistry meters,
                                             Clock clock) {
        this.incomingPayments = incomingPayments;
        this.posting = posting;
        this.outbox = outbox;
        this.properties = properties;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void notify(NotifyIncomingPaymentCommand command) {
        Money amount = Money.of(command.amount(), command.currency());
        Instant now = clock.instant();
        int threshold = properties.incoming().confirmationThreshold();

        IncomingPayment payment = incomingPayments
                .lockByTxOutput(command.txHash(), command.outputIndex())
                .orElse(null);

        boolean freshlyInserted = false;
        boolean dirty = false;
        String result;
        if (payment == null) {
            payment = IncomingPayment.observed(command.merchantId(), command.txHash(),
                    command.outputIndex(), amount, command.confirmations(), now);
            try {
                payment = incomingPayments.insert(payment);
            } catch (IncomingPaymentRepository.DuplicateException e) {
                // Another notifier inserted the same output first — retryable.
                throw new ConcurrentRequestException(command.txHash() + ":" + command.outputIndex());
            }
            freshlyInserted = true;
            result = "observed";
        } else {
            payment.assertMatches(command.merchantId(), amount);   // 409 on contradictory replay
            dirty = payment.recordConfirmations(command.confirmations(), now);
            result = dirty ? "updated" : "duplicate";
        }

        UUID operationId = UUID.randomUUID();
        if (payment.confirm(threshold, operationId, now)) {
            posting.post(command.merchantId(), LedgerEntryType.INCOMING_CREDIT, amount, operationId,
                    "incoming " + command.txHash() + ":" + command.outputIndex());
            incomingPayments.save(payment);
            outbox.append(OutboxEvent.create(
                    OutboxEventTypes.AGG_INCOMING_PAYMENT, payment.id().toString(),
                    OutboxEventTypes.INCOMING_PAYMENT_CONFIRMED,
                    "{\"incomingPaymentId\":\"" + payment.id() + "\"}", now));
            result = "confirmed";
        } else if (dirty && !freshlyInserted) {
            incomingPayments.save(payment);
        }

        meters.counter("wallet.incoming.notifications", "result", result).increment();
        audit.info("incoming_payment_notify merchant={} tx={}:{} confirmations={} status={} result={}",
                command.merchantId(), command.txHash(), command.outputIndex(),
                payment.confirmations(), payment.status(), result);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncomingPaymentView> forMerchant(long merchantId) {
        return incomingPayments.findByMerchant(merchantId).stream()
                .map(IncomingPaymentView::of)
                .toList();
    }
}
