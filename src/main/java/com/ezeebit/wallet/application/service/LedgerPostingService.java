package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.out.AccountRepository;
import com.ezeebit.wallet.application.port.out.LedgerRepository;
import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.LedgerEntry;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * The single writer of balances and ledger entries. Every balance movement in the
 * system goes through {@link #post}: it takes the account row lock, applies the
 * movement (which enforces no-overdraft and no currency mixing), writes the cached
 * balance, and appends one immutable ledger line recording the running balance.
 *
 * <p>Must be called within a transaction owned by the calling use case, so that the
 * balance update and the ledger append commit atomically.
 */
@Service
public class LedgerPostingService {

    private static final Logger audit = LoggerFactory.getLogger("wallet.audit");

    private final AccountRepository accounts;
    private final LedgerRepository ledger;
    private final MeterRegistry meters;

    public LedgerPostingService(AccountRepository accounts, LedgerRepository ledger, MeterRegistry meters) {
        this.accounts = accounts;
        this.ledger = ledger;
        this.meters = meters;
    }

    /**
     * Apply a movement of {@code magnitude} (always non-negative) to the merchant's
     * account for that currency, in the direction given by {@code type}.
     *
     * @return the account with its updated balance
     */
    public Account post(long merchantId, LedgerEntryType type, Money magnitude,
                        UUID operationId, String reference) {
        Account account = accounts.lockForUpdate(merchantId, magnitude.currency())
                .orElseGet(() -> accounts.save(Account.opened(merchantId, magnitude.currency())));

        int sign = type.signum();
        Money signedAmount;
        if (sign > 0) {
            account.credit(magnitude);
            signedAmount = magnitude;
        } else if (sign < 0) {
            account.debit(magnitude);              // throws InsufficientFundsException if short
            signedAmount = magnitude.negate();
        } else {
            signedAmount = Money.zero(magnitude.currency());   // pure audit marker
        }

        Account saved = accounts.save(account);
        ledger.append(new LedgerEntry(null, saved.id(), merchantId, type, signedAmount,
                operationId, saved.balance(), reference, null));

        // Observability: a metric per entry type and a structured audit line for every movement.
        meters.counter("wallet.ledger.entries", "type", type.name(),
                "currency", magnitude.currency().name()).increment();
        audit.info("ledger_post merchant={} type={} amount={} balance_after={} operation={} ref={}",
                merchantId, type, signedAmount.amount().toPlainString(),
                saved.balance().amount().toPlainString(), operationId, reference);
        return saved;
    }
}
