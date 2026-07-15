package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.IncomingPayment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface GetIncomingPaymentsUseCase {

    List<IncomingPaymentView> forMerchant(long merchantId);

    record IncomingPaymentView(String id, long merchantId, String txHash, int outputIndex,
                               Currency currency, BigDecimal amount, int confirmations,
                               String status, String settleStatus, String settlementConversionId,
                               Instant firstSeenAt, Instant confirmedAt) {

        public static IncomingPaymentView of(IncomingPayment p) {
            return new IncomingPaymentView(p.id().toString(), p.merchantId(), p.txHash(),
                    p.outputIndex(), p.amount().currency(), p.amount().amount(), p.confirmations(),
                    p.status().name(), p.settleStatus().name(),
                    p.settlementConversionId() == null ? null : p.settlementConversionId().toString(),
                    p.firstSeenAt(), p.confirmedAt());
        }
    }
}
