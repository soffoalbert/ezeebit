package com.ezeebit.wallet.adapter.out.persistence;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface ConversionQuoteJpaRepository extends JpaRepository<ConversionQuoteEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ConversionQuoteEntity q where q.id = :id")
    Optional<ConversionQuoteEntity> lockById(@Param("id") String id);
}
