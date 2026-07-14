package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.OutboxEvent;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository {

    OutboxEvent append(OutboxEvent event);

    OutboxEvent save(OutboxEvent event);

    /**
     * Claim up to {@code limit} events that are due for processing, taking a row lock and
     * skipping rows already locked by another relay (SELECT … FOR UPDATE SKIP LOCKED).
     * "Due" means status PENDING with {@code nextAttemptAt <= now}, or status PROCESSING
     * that was claimed before {@code staleBefore} (recovering a crashed relay). Must be
     * called inside a transaction.
     */
    List<OutboxEvent> claimDue(Instant now, Instant staleBefore, int limit);
}
