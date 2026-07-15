package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.IncomingPayment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

interface IncomingPaymentJpaRepository extends JpaRepository<IncomingPaymentEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from IncomingPaymentEntity p where p.txHash = :txHash and p.outputIndex = :vout")
    Optional<IncomingPaymentEntity> lockByTxOutput(@Param("txHash") String txHash,
                                                   @Param("vout") int outputIndex);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from IncomingPaymentEntity p where p.id = :id")
    Optional<IncomingPaymentEntity> lockById(@Param("id") String id);

    List<IncomingPaymentEntity> findByMerchantIdOrderByFirstSeenAtDesc(long merchantId);

    @Query("select p.currency as currency, sum(p.amount) as total from IncomingPaymentEntity p "
            + "where p.merchantId = :merchantId and p.status = :status group by p.currency")
    List<PendingTotalRow> pendingTotals(@Param("merchantId") long merchantId,
                                        @Param("status") IncomingPayment.Status status);

    interface PendingTotalRow {
        Currency getCurrency();
        BigDecimal getTotal();
    }
}
