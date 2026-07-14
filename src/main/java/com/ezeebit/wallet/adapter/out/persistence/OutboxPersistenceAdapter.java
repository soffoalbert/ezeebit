package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.OutboxRepository;
import com.ezeebit.wallet.domain.model.OutboxEvent;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
class OutboxPersistenceAdapter implements OutboxRepository {

    private final OutboxJpaRepository jpa;

    OutboxPersistenceAdapter(OutboxJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public OutboxEvent append(OutboxEvent event) {
        return jpa.save(OutboxEventEntity.fromDomain(event)).toDomain();
    }

    @Override
    public OutboxEvent save(OutboxEvent event) {
        return jpa.save(OutboxEventEntity.fromDomain(event)).toDomain();
    }

    @Override
    public List<OutboxEvent> claimDue(Instant now, Instant staleBefore, int limit) {
        List<OutboxEventEntity> rows = jpa.findDue(now, staleBefore,
                OutboxEvent.Status.PENDING, OutboxEvent.Status.PROCESSING, PageRequest.of(0, limit));
        List<OutboxEvent> claimed = new ArrayList<>(rows.size());
        for (OutboxEventEntity row : rows) {
            OutboxEvent event = row.toDomain();
            event.markProcessing(now);
            jpa.save(OutboxEventEntity.fromDomain(event));   // flip to PROCESSING within the claim tx
            claimed.add(event);
        }
        return claimed;
    }
}
