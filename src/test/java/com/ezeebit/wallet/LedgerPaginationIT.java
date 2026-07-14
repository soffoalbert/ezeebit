package com.ezeebit.wallet;

import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase.DepositCommand;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase.LedgerPage;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerPaginationIT extends AbstractIntegrationTest {

    private static final long MERCHANT = 1L;

    @Autowired
    DepositFundsUseCase deposit;
    @Autowired
    GetLedgerHistoryUseCase getLedger;

    @Test
    void cursorPaginationWalksAllEntriesNewestFirstWithoutDuplicates() {
        for (int i = 1; i <= 5; i++) {
            deposit.deposit(new DepositCommand(MERCHANT, Currency.ZAR,
                    new BigDecimal(i + ".00"), "dep-" + i, "n" + i));
        }

        List<Long> seen = new ArrayList<>();
        Long cursor = null;
        int pages = 0;
        do {
            LedgerPage page = getLedger.history(MERCHANT, Currency.ZAR, cursor, 2);
            page.entries().forEach(e -> seen.add(e.id()));
            cursor = page.nextCursor();
            pages++;
        } while (cursor != null && pages < 10);

        // All five entries seen exactly once, strictly descending by id (newest first).
        assertThat(seen).hasSize(5).doesNotHaveDuplicates().isSortedAccordingTo((a, b) -> b.compareTo(a));
    }
}
