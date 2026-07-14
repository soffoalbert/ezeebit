package com.ezeebit.wallet.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A durable record of something that must happen outside the current transaction,
 * written in the same transaction as the business change it follows. A relay claims
 * due events, dispatches them at-least-once to an idempotent handler, and retries with
 * backoff on failure. This survives a process crash, unlike an in-memory post-commit hook.
 */
public final class OutboxEvent {

    public enum Status { PENDING, PROCESSING, PROCESSED, FAILED }

    public static final int MAX_ATTEMPTS = 10;

    private final Long id;                 // null until persisted
    private final String aggregateType;
    private final String aggregateId;
    private final String eventType;
    private final String payload;
    private Status status;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant claimedAt;
    private String lastError;
    private final Instant createdAt;
    private Instant processedAt;

    public OutboxEvent(Long id, String aggregateType, String aggregateId, String eventType,
                       String payload, Status status, int attempts, Instant nextAttemptAt,
                       Instant claimedAt, String lastError, Instant createdAt, Instant processedAt) {
        this.id = id;
        this.aggregateType = Objects.requireNonNull(aggregateType);
        this.aggregateId = Objects.requireNonNull(aggregateId);
        this.eventType = Objects.requireNonNull(eventType);
        this.payload = Objects.requireNonNull(payload);
        this.status = Objects.requireNonNull(status);
        this.attempts = attempts;
        this.nextAttemptAt = Objects.requireNonNull(nextAttemptAt);
        this.claimedAt = claimedAt;
        this.lastError = lastError;
        this.createdAt = createdAt;
        this.processedAt = processedAt;
    }

    public static OutboxEvent create(String aggregateType, String aggregateId, String eventType,
                                     String payload, Instant now) {
        return new OutboxEvent(null, aggregateType, aggregateId, eventType, payload,
                Status.PENDING, 0, now, null, null, now, null);
    }

    /** Mark that a relay has taken ownership of this event for processing. */
    public void markProcessing(Instant now) {
        this.status = Status.PROCESSING;
        this.claimedAt = now;
    }

    public void markProcessed(Instant now) {
        this.status = Status.PROCESSED;
        this.processedAt = now;
        this.claimedAt = null;
        this.lastError = null;
    }

    /** Record a failed attempt, scheduling a backed-off retry or giving up after the cap. */
    public void markFailed(String error, Instant now) {
        this.attempts += 1;
        this.claimedAt = null;
        this.lastError = truncate(error);
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = Status.FAILED;   // parked for manual intervention / alerting
        } else {
            this.status = Status.PENDING;
            this.nextAttemptAt = now.plus(backoff(this.attempts));
        }
    }

    /** Exponential backoff capped at 5 minutes: 1s, 2s, 4s, 8s, ... */
    private static Duration backoff(int attempts) {
        long seconds = Math.min(300, (long) Math.pow(2, Math.min(attempts, 8)));
        return Duration.ofSeconds(seconds);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 500 ? s : s.substring(0, 500);
    }

    public Long id() { return id; }
    public String aggregateType() { return aggregateType; }
    public String aggregateId() { return aggregateId; }
    public String eventType() { return eventType; }
    public String payload() { return payload; }
    public Status status() { return status; }
    public int attempts() { return attempts; }
    public Instant nextAttemptAt() { return nextAttemptAt; }
    public Instant claimedAt() { return claimedAt; }
    public String lastError() { return lastError; }
    public Instant createdAt() { return createdAt; }
    public Instant processedAt() { return processedAt; }
}
