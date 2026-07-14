package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.out.IdempotencyStore;
import com.ezeebit.wallet.application.port.out.Serializer;
import com.ezeebit.wallet.domain.exception.ConcurrentRequestException;
import com.ezeebit.wallet.domain.exception.IdempotencyConflictException;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * Wraps a mutating operation so that a retry with the same idempotency key returns the
 * original result instead of repeating the effect.
 *
 * <p>Correctness under concurrency: the operation and the {@link IdempotencyStore#save}
 * run inside the caller's transaction. Two simultaneous requests with the same key both
 * do the work, but only one {@code save} can win the unique constraint; the loser gets a
 * {@link IdempotencyStore.DuplicateKeyException}, which we surface as a retryable
 * {@link ConcurrentRequestException} so its transaction rolls back and no double effect
 * is committed. The client's retry then hits the cached result. Sequential retries (the
 * common mobile case) always hit the cache directly.
 */
@Component
public class IdempotencyGuard {

    private final IdempotencyStore store;
    private final Serializer serializer;

    public IdempotencyGuard(IdempotencyStore store, Serializer serializer) {
        this.store = store;
        this.serializer = serializer;
    }

    public <T> T execute(long merchantId, String endpoint, String key, String requestHash,
                         Class<T> resultType, Supplier<T> action) {
        if (key == null || key.isBlank()) {
            // No key supplied: the caller opted out of idempotency.
            return action.get();
        }

        Optional<IdempotencyStore.StoredResponse> existing = store.find(merchantId, endpoint, key);
        if (existing.isPresent()) {
            return replay(existing.get(), key, requestHash, resultType);
        }

        T result = action.get();
        try {
            store.save(merchantId, endpoint, key, requestHash, serializer.toJson(result));
        } catch (IdempotencyStore.DuplicateKeyException e) {
            // A concurrent request committed first. Roll back our own effect and ask the
            // client to retry, at which point it will read the winner's stored result.
            throw new ConcurrentRequestException(key);
        }
        return result;
    }

    private <T> T replay(IdempotencyStore.StoredResponse stored, String key,
                         String requestHash, Class<T> resultType) {
        if (!stored.requestHash().equals(requestHash)) {
            throw new IdempotencyConflictException(key);
        }
        return serializer.fromJson(stored.responseJson(), resultType);
    }
}
