package com.ezeebit.wallet.adapter.in.scheduling;

import com.ezeebit.wallet.application.port.out.OutboxRepository;
import com.ezeebit.wallet.application.service.OutboxEventDispatcher;
import com.ezeebit.wallet.domain.model.OutboxEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Polls the transactional outbox and dispatches due events. Events are claimed in one
 * short transaction (row-locked, skipping rows another relay holds), then each is
 * dispatched in its own transaction and marked PROCESSED or retried with backoff.
 *
 * <p>Because claiming and the business write share a transaction and the dispatch target
 * is idempotent, an event is processed effectively exactly once and survives a crash at
 * any point (a claimed-but-unfinished event is reclaimed once its claim goes stale).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH = 100;
    private static final Duration STALE_CLAIM = Duration.ofMinutes(2);

    private final OutboxRepository outbox;
    private final OutboxEventDispatcher dispatcher;
    private final TransactionTemplate txTemplate;
    private final MeterRegistry meters;
    private final Clock clock;

    OutboxRelay(OutboxRepository outbox, OutboxEventDispatcher dispatcher,
                PlatformTransactionManager txManager, MeterRegistry meters, Clock clock) {
        this.outbox = outbox;
        this.dispatcher = dispatcher;
        this.txTemplate = new TransactionTemplate(txManager);
        this.meters = meters;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wallet.outbox.poll-interval-ms:1000}",
               initialDelayString = "${wallet.outbox.poll-interval-ms:1000}")
    public void poll() {
        Instant now = clock.instant();
        Instant staleBefore = now.minus(STALE_CLAIM);

        List<OutboxEvent> claimed = txTemplate.execute(s ->
                outbox.claimDue(now, staleBefore, BATCH));
        if (claimed == null || claimed.isEmpty()) {
            return;
        }

        for (OutboxEvent event : claimed) {
            process(event);
        }
    }

    private void process(OutboxEvent event) {
        try {
            // Dispatch in its own transaction (the target use case is @Transactional).
            dispatcher.dispatch(event);
            event.markProcessed(clock.instant());
            meters.counter("wallet.outbox.processed", "event", event.eventType()).increment();
        } catch (RuntimeException e) {
            event.markFailed(e.getMessage(), clock.instant());
            meters.counter("wallet.outbox.failed", "event", event.eventType()).increment();
            log.warn("outbox event {} (type={}) failed on attempt {}: {}",
                    event.id(), event.eventType(), event.attempts(), e.getMessage());
        }
        // Persist the new status in its own transaction, independent of the dispatch.
        txTemplate.executeWithoutResult(s -> outbox.save(event));
    }
}
