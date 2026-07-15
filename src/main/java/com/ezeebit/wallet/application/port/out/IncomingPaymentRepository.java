package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.IncomingPayment;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface IncomingPaymentRepository {

    /** Insert a first-seen payment. Throws {@link DuplicateException} if the (tx, vout) already exists. */
    IncomingPayment insert(IncomingPayment payment);

    IncomingPayment save(IncomingPayment payment);

    /** Row-lock the payment for a (tx_hash, output_index), the dedupe key. */
    Optional<IncomingPayment> lockByTxOutput(String txHash, int outputIndex);

    Optional<IncomingPayment> lockById(UUID id);

    List<IncomingPayment> findByMerchant(long merchantId);

    /** PENDING per-currency totals for a merchant — the "pendingIncoming" shown on balances. */
    Map<Currency, BigDecimal> pendingTotals(long merchantId);

    /** A (tx_hash, output_index) uniqueness violation lost the first-insert race. */
    class DuplicateException extends RuntimeException {
        public DuplicateException(String txHash, int outputIndex) {
            super("duplicate incoming payment " + txHash + ":" + outputIndex);
        }
    }
}
