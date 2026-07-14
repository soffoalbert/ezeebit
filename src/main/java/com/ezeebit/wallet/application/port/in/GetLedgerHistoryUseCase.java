package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.LedgerEntry;
import com.ezeebit.wallet.domain.model.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface GetLedgerHistoryUseCase {

    List<LedgerEntryView> history(long merchantId, Currency currency, int limit, int offset);

    record LedgerEntryView(long id, LedgerEntryType type, BigDecimal amount,
                           BigDecimal balanceAfter, String operationId, String reference,
                           Instant createdAt) {
        public static LedgerEntryView of(LedgerEntry e) {
            return new LedgerEntryView(e.id(), e.type(), e.amount().amount(),
                    e.balanceAfter().amount(), e.operationId().toString(), e.reference(), e.createdAt());
        }
    }
}
