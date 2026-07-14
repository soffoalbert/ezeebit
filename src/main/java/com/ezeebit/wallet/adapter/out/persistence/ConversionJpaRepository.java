package com.ezeebit.wallet.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface ConversionJpaRepository extends JpaRepository<ConversionEntity, String> {
    Optional<ConversionEntity> findByQuoteId(String quoteId);
}
