package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Quote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

interface ConversionQuoteJpaRepository extends JpaRepository<ConversionQuoteEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ConversionQuoteEntity q where q.id = :id")
    Optional<ConversionQuoteEntity> lockById(@Param("id") String id);

    @Modifying
    @Query("update ConversionQuoteEntity q set q.status = :expired "
            + "where q.status = :active and q.expiresAt < :now")
    int expireActiveOlderThan(@Param("now") Instant now,
                              @Param("active") Quote.Status active,
                              @Param("expired") Quote.Status expired);
}
