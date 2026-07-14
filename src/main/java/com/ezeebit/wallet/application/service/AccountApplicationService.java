package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.BalanceView;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.GetBalancesUseCase;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase;
import com.ezeebit.wallet.application.port.out.AccountRepository;
import com.ezeebit.wallet.application.port.out.LedgerRepository;
import com.ezeebit.wallet.application.service.support.RequestHash;
import com.ezeebit.wallet.domain.exception.AccountNotFoundException;
import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Task 1 — hold a merchant's money across currencies. Handles deposits (idempotently),
 * balance queries, and the audit history that explains how a balance reached its value.
 */
@Service
public class AccountApplicationService
        implements DepositFundsUseCase, GetBalancesUseCase, GetLedgerHistoryUseCase {

    private static final String ENDPOINT = "deposit";

    private final LedgerPostingService posting;
    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final IdempotencyGuard idempotency;

    public AccountApplicationService(LedgerPostingService posting, AccountRepository accounts,
                                     LedgerRepository ledger, IdempotencyGuard idempotency) {
        this.posting = posting;
        this.accounts = accounts;
        this.ledger = ledger;
        this.idempotency = idempotency;
    }

    @Override
    @Transactional
    public BalanceView deposit(DepositCommand command) {
        Money amount = Money.of(command.amount(), command.currency());
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("deposit amount must be positive");
        }
        String hash = RequestHash.of(command.merchantId(), command.currency(),
                amount.amount(), command.reference());

        return idempotency.execute(command.merchantId(), ENDPOINT, command.idempotencyKey(), hash,
                BalanceView.class, () -> {
                    Account account = posting.post(command.merchantId(), LedgerEntryType.DEPOSIT,
                            amount, UUID.randomUUID(), command.reference());
                    return BalanceView.of(account);
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<BalanceView> balancesOf(long merchantId) {
        return accounts.findAllForMerchant(merchantId).stream()
                .map(BalanceView::of)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GetLedgerHistoryUseCase.LedgerEntryView> history(long merchantId, Currency currency,
                                                                 int limit, int offset) {
        Account account = accounts.find(merchantId, currency)
                .orElseThrow(() -> new AccountNotFoundException(merchantId, currency));
        return ledger.findByAccount(account.id(), limit, offset).stream()
                .map(GetLedgerHistoryUseCase.LedgerEntryView::of)
                .toList();
    }
}
