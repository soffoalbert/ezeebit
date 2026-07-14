package com.ezeebit.wallet.application.port.out;

import java.util.Optional;

/**
 * Stores the outcome of a mutating request keyed by (merchant, endpoint, client key)
 * so retries return the original result instead of repeating the effect.
 */
public interface IdempotencyStore {

    Optional<StoredResponse> find(long merchantId, String endpoint, String key);

    /**
     * Persist the response for a key. Implementations rely on a unique constraint;
     * a concurrent insert of the same key must surface as
     * {@link DuplicateKeyException} so the caller can fall back to {@link #find}.
     */
    void save(long merchantId, String endpoint, String key, String requestHash, String responseJson);

    record StoredResponse(String requestHash, String responseJson) {}

    /** Thrown when a concurrent request already inserted the same key. */
    class DuplicateKeyException extends RuntimeException {
        public DuplicateKeyException(Throwable cause) {
            super(cause);
        }
    }
}
