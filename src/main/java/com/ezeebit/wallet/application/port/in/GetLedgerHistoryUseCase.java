package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.LedgerEntry;
import com.ezeebit.wallet.domain.model.LedgerEntryType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface GetLedgerHistoryUseCase {

    /**
     * A page of ledger entries newest-first. {@code before} is a cursor: pass {@code null}
     * for the first page, then the returned {@code nextCursor} to fetch older entries.
     * This is stable under concurrent appends, unlike offset paging.
     */
    LedgerPage history(long merchantId, Currency currency, Long before, int limit);

    record LedgerPage(List<LedgerEntryView> entries, Long nextCursor) {}

    record LedgerEntryView(long id, LedgerEntryType type, BigDecimal amount,
                           BigDecimal balanceAfter, String operationId, String reference,
                           Instant createdAt) {
        public static LedgerEntryView of(LedgerEntry e) {
            return new LedgerEntryView(e.id(), e.type(), e.amount().amount(),
                    e.balanceAfter().amount(), e.operationId().toString(), e.reference(), e.createdAt());
        }
    }
}
