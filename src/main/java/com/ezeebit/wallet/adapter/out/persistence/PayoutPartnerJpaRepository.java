package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface PayoutPartnerJpaRepository extends JpaRepository<PayoutPartnerEntity, Long> {

    @Query("select p from PayoutPartnerEntity p where p.currency = :currency "
            + "and (p.country is null or p.country = :country) order by p.priority asc")
    List<PayoutPartnerEntity> findRoutes(@Param("country") String country,
                                         @Param("currency") Currency currency);

    Optional<PayoutPartnerEntity> findByCode(String code);

    List<PayoutPartnerEntity> findAllByOrderByCurrencyAscPriorityAsc();

    @Modifying
    @Query("update PayoutPartnerEntity p set p.healthy = :healthy where p.code = :code")
    int updateHealthy(@Param("code") String code, @Param("healthy") boolean healthy);
}
