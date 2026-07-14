package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface AccountJpaRepository extends JpaRepository<AccountEntity, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AccountEntity a where a.merchantId = :merchantId and a.currency = :currency")
    Optional<AccountEntity> lockByMerchantAndCurrency(@Param("merchantId") long merchantId,
                                                      @Param("currency") Currency currency);

    Optional<AccountEntity> findByMerchantIdAndCurrency(long merchantId, Currency currency);

    List<AccountEntity> findByMerchantIdOrderByCurrency(long merchantId);
}
