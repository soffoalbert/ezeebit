package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.IdempotencyStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class IdempotencyPersistenceAdapter implements IdempotencyStore {

    private final IdempotencyJpaRepository jpa;

    IdempotencyPersistenceAdapter(IdempotencyJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Optional<StoredResponse> find(long merchantId, String endpoint, String key) {
        return jpa.findByMerchantIdAndEndpointAndIdemKey(merchantId, endpoint, key)
                .map(e -> new StoredResponse(e.getRequestHash(), e.getResponseJson()));
    }

    @Override
    public void save(long merchantId, String endpoint, String key, String requestHash, String responseJson) {
        try {
            // Flush now so a unique-constraint violation surfaces here rather than at commit.
            jpa.saveAndFlush(new IdempotencyRecordEntity(
                    merchantId, endpoint, key, requestHash, responseJson));
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateKeyException(e);
        }
    }
}
