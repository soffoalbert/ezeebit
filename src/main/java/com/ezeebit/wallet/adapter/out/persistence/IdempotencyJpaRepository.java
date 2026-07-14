package com.ezeebit.wallet.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface IdempotencyJpaRepository extends JpaRepository<IdempotencyRecordEntity, Long> {
    Optional<IdempotencyRecordEntity> findByMerchantIdAndEndpointAndIdemKey(
            long merchantId, String endpoint, String idemKey);
}
