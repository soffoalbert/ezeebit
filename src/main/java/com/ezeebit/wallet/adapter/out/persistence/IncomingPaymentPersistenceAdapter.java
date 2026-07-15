package com.ezeebit.wallet.adapter.out.persistence;

import com.ezeebit.wallet.application.port.out.IncomingPaymentRepository;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.IncomingPayment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Component
class IncomingPaymentPersistenceAdapter implements IncomingPaymentRepository {

    private final IncomingPaymentJpaRepository jpa;

    IncomingPaymentPersistenceAdapter(IncomingPaymentJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public IncomingPayment insert(IncomingPayment payment) {
        try {
            return jpa.saveAndFlush(IncomingPaymentEntity.fromDomain(payment)).toDomain();
        } catch (DataIntegrityViolationException e) {
            // Another notifier inserted the same (tx_hash, output_index) first.
            throw new DuplicateException(payment.txHash(), payment.outputIndex());
        }
    }

    @Override
    public IncomingPayment save(IncomingPayment payment) {
        return jpa.save(IncomingPaymentEntity.fromDomain(payment)).toDomain();
    }

    @Override
    public Optional<IncomingPayment> lockByTxOutput(String txHash, int outputIndex) {
        return jpa.lockByTxOutput(txHash, outputIndex).map(IncomingPaymentEntity::toDomain);
    }

    @Override
    public Optional<IncomingPayment> lockById(UUID id) {
        return jpa.lockById(id.toString()).map(IncomingPaymentEntity::toDomain);
    }

    @Override
    public List<IncomingPayment> findByMerchant(long merchantId) {
        return jpa.findByMerchantIdOrderByFirstSeenAtDesc(merchantId).stream()
                .map(IncomingPaymentEntity::toDomain)
                .toList();
    }

    @Override
    public Map<Currency, BigDecimal> pendingTotals(long merchantId) {
        Map<Currency, BigDecimal> totals = new EnumMap<>(Currency.class);
        for (var row : jpa.pendingTotals(merchantId, IncomingPayment.Status.PENDING)) {
            Currency currency = row.getCurrency();
            totals.put(currency, row.getTotal().setScale(currency.scale(), java.math.RoundingMode.DOWN));
        }
        return totals;
    }
}
