package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.BalanceView;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.GetBalancesUseCase;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase;
import com.ezeebit.wallet.application.port.out.AccountRepository;
import com.ezeebit.wallet.application.port.out.IncomingPaymentRepository;
import com.ezeebit.wallet.application.port.out.LedgerRepository;
import com.ezeebit.wallet.application.service.support.RequestHash;
import com.ezeebit.wallet.domain.exception.AccountNotFoundException;
import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final IncomingPaymentRepository incomingPayments;
    private final IdempotencyGuard idempotency;

    public AccountApplicationService(LedgerPostingService posting, AccountRepository accounts,
                                     LedgerRepository ledger, IncomingPaymentRepository incomingPayments,
                                     IdempotencyGuard idempotency) {
        this.posting = posting;
        this.accounts = accounts;
        this.ledger = ledger;
        this.incomingPayments = incomingPayments;
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
        Map<Currency, BigDecimal> pending = incomingPayments.pendingTotals(merchantId);
        Map<Currency, BigDecimal> spendable = new java.util.EnumMap<>(Currency.class);
        accounts.findAllForMerchant(merchantId)
                .forEach(a -> spendable.put(a.currency(), a.balance().amount()));

        // Union of currencies the merchant has any spendable or pending money in.
        Set<Currency> currencies = new LinkedHashSet<>(spendable.keySet());
        currencies.addAll(pending.keySet());

        List<BalanceView> views = new ArrayList<>();
        for (Currency currency : currencies) {
            views.add(new BalanceView(merchantId, currency,
                    spendable.getOrDefault(currency, BigDecimal.ZERO),
                    pending.getOrDefault(currency, BigDecimal.ZERO)));
        }
        return views;
    }

    @Override
    @Transactional(readOnly = true)
    public GetLedgerHistoryUseCase.LedgerPage history(long merchantId, Currency currency,
                                                      Long before, int limit) {
        Account account = accounts.find(merchantId, currency)
                .orElseThrow(() -> new AccountNotFoundException(merchantId, currency));
        int size = limit <= 0 ? 50 : Math.min(limit, 200);
        List<GetLedgerHistoryUseCase.LedgerEntryView> entries =
                ledger.findByAccount(account.id(), before, size).stream()
                        .map(GetLedgerHistoryUseCase.LedgerEntryView::of)
                        .toList();
        // A full page implies there may be more; the cursor is the oldest id returned.
        Long nextCursor = entries.size() == size
                ? entries.get(entries.size() - 1).id()
                : null;
        return new GetLedgerHistoryUseCase.LedgerPage(entries, nextCursor);
    }
}
