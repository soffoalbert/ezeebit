package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.OutboxEvent;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "outbox_event")
class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 32, updatable = false)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64, updatable = false)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64, updatable = false)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "json", updatable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OutboxEvent.Status status;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    protected OutboxEventEntity() {}

    static OutboxEventEntity fromDomain(OutboxEvent e) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = e.id();
        entity.aggregateType = e.aggregateType();
        entity.aggregateId = e.aggregateId();
        entity.eventType = e.eventType();
        entity.payload = e.payload();
        entity.status = e.status();
        entity.attempts = e.attempts();
        entity.nextAttemptAt = e.nextAttemptAt();
        entity.claimedAt = e.claimedAt();
        entity.lastError = e.lastError();
        entity.createdAt = e.createdAt();
        entity.processedAt = e.processedAt();
        return entity;
    }

    OutboxEvent toDomain() {
        return new OutboxEvent(id, aggregateType, aggregateId, eventType, payload, status,
                attempts, nextAttemptAt, claimedAt, lastError, createdAt, processedAt);
    }
}
