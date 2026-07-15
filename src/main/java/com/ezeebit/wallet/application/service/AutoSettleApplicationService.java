package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.ExecuteAutoSettleUseCase;
import com.ezeebit.wallet.application.port.in.ManageAutoSettleRulesUseCase;
import com.ezeebit.wallet.application.port.out.AutoSettleRuleRepository;
import com.ezeebit.wallet.application.port.out.ConversionRepository;
import com.ezeebit.wallet.application.port.out.IncomingPaymentRepository;
import com.ezeebit.wallet.application.service.support.ConversionPricer;
import com.ezeebit.wallet.domain.model.AutoSettleRule;
import com.ezeebit.wallet.domain.model.Conversion;
import com.ezeebit.wallet.domain.model.IncomingPayment;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Task 5 — auto-settle: convert a merchant-configured percentage of a confirmed incoming
 * payment into their local fiat at the market rate at execution time.
 *
 * <p>Driven by the outbox after an incoming payment confirms. Idempotency rests entirely on
 * the payment's {@code settleStatus} state machine (REQUESTED → SETTLED|SKIPPED): a redelivery
 * finds it no longer REQUESTED and does nothing, so the conversion can never happen twice.
 * A transient rate failure propagates so the transaction rolls back and the outbox retries with
 * backoff — leaving the payment REQUESTED until the feed recovers.
 */
@Service
public class AutoSettleApplicationService
        implements ExecuteAutoSettleUseCase, ManageAutoSettleRulesUseCase {

    private static final Logger audit = LoggerFactory.getLogger("wallet.audit");

    private final IncomingPaymentRepository incomingPayments;
    private final AutoSettleRuleRepository rules;
    private final ConversionRepository conversions;
    private final ConversionPricer pricer;
    private final LedgerPostingService posting;
    private final MeterRegistry meters;
    private final Clock clock;

    public AutoSettleApplicationService(IncomingPaymentRepository incomingPayments,
                                        AutoSettleRuleRepository rules, ConversionRepository conversions,
                                        ConversionPricer pricer, LedgerPostingService posting,
                                        MeterRegistry meters, Clock clock) {
        this.incomingPayments = incomingPayments;
        this.rules = rules;
        this.conversions = conversions;
        this.pricer = pricer;
        this.posting = posting;
        this.meters = meters;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void settle(UUID incomingPaymentId) {
        IncomingPayment payment = incomingPayments.lockById(incomingPaymentId).orElse(null);
        if (payment == null || payment.settleStatus() != IncomingPayment.SettleStatus.REQUESTED) {
            return;   // not found, already settled/skipped, or not yet confirmed — nothing to do
        }
        Instant now = clock.instant();

        Optional<AutoSettleRule> maybeRule = rules.find(payment.merchantId(), payment.amount().currency());
        if (maybeRule.isEmpty() || !maybeRule.get().enabled()) {
            skip(payment, "no-rule", now);
            return;
        }
        AutoSettleRule rule = maybeRule.get();

        Money portion = rule.portionOf(payment.amount());
        if (!portion.isPositive()) {
            skip(payment, "zero-portion", now);
            return;
        }

        // Market rate at execution time — may throw RateUnavailable/InvalidRate, which propagates
        // so the outbox retries (payment stays REQUESTED).
        ConversionPricer.PricedConversion priced =
                pricer.price(portion.currency(), rule.targetCurrency(), portion);

        UUID operationId = UUID.randomUUID();
        String ref = "auto-settle incoming " + payment.id();
        posting.post(payment.merchantId(), LedgerEntryType.CONVERSION_OUT, portion, operationId, ref);
        posting.post(payment.merchantId(), LedgerEntryType.CONVERSION_IN, priced.toAmount(), operationId, ref);

        Conversion conversion = Conversion.autoSettled(payment.merchantId(), payment.id(),
                portion, priced.toAmount(), priced.effectiveRate(), operationId, now);
        conversions.save(conversion);

        payment.markSettled(conversion.id(), now);
        incomingPayments.save(payment);

        meters.counter("wallet.autosettle.executed",
                "source", portion.currency().name(), "target", rule.targetCurrency().name()).increment();
        audit.info("auto_settle_executed payment={} merchant={} from={} to={} conversion={}",
                payment.id(), payment.merchantId(), portion.amount().toPlainString(),
                priced.toAmount().amount().toPlainString(), conversion.id());
    }

    private void skip(IncomingPayment payment, String reason, Instant now) {
        payment.markSkipped(now);
        incomingPayments.save(payment);
        meters.counter("wallet.autosettle.skipped", "reason", reason).increment();
        audit.info("auto_settle_skipped payment={} merchant={} reason={}",
                payment.id(), payment.merchantId(), reason);
    }

    @Override
    @Transactional
    public AutoSettleRuleView upsert(UpsertAutoSettleRuleCommand command) {
        // The domain constructor enforces the invariants (stablecoin source, fiat target,
        // percentage in (0,100]); a violation surfaces as a 400.
        AutoSettleRule rule = AutoSettleRule.of(command.merchantId(), command.sourceCurrency(),
                command.targetCurrency(), command.percentage(), command.enabled(), clock.instant());
        return AutoSettleRuleView.of(rules.upsert(rule));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutoSettleRuleView> forMerchant(long merchantId) {
        return rules.findAllForMerchant(merchantId).stream()
                .map(AutoSettleRuleView::of)
                .toList();
    }
}
