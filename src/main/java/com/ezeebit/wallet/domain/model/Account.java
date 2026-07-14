package com.ezeebit.wallet.domain.model;

import com.ezeebit.wallet.domain.exception.CurrencyMismatchException;
import com.ezeebit.wallet.domain.exception.InsufficientFundsException;

import java.util.Objects;

/**
 * A merchant's balance in one currency. The balance held here is a cached
 * projection of the ledger; it is only ever moved through {@link #credit} and
 * {@link #debit}, which enforce that a merchant can never go negative and that
 * the currency of the movement matches the account.
 */
public final class Account {

    private final Long id;            // null until persisted
    private final long merchantId;
    private final Currency currency;
    private Money balance;
    private long version;

    public Account(Long id, long merchantId, Currency currency, Money balance, long version) {
        this.id = id;
        this.merchantId = merchantId;
        this.currency = Objects.requireNonNull(currency, "currency");
        this.balance = Objects.requireNonNull(balance, "balance");
        this.version = version;
        if (balance.currency() != currency) {
            throw new CurrencyMismatchException(currency, balance.currency());
        }
    }

    public static Account opened(long merchantId, Currency currency) {
        return new Account(null, merchantId, currency, Money.zero(currency), 0);
    }

    public void credit(Money amount) {
        requireCurrency(amount);
        if (amount.isNegative()) {
            throw new IllegalArgumentException("credit amount must not be negative: " + amount);
        }
        this.balance = this.balance.add(amount);
    }

    public void debit(Money amount) {
        requireCurrency(amount);
        if (amount.isNegative()) {
            throw new IllegalArgumentException("debit amount must not be negative: " + amount);
        }
        if (amount.isGreaterThan(this.balance)) {
            throw new InsufficientFundsException(merchantId, currency, balance, amount);
        }
        this.balance = this.balance.subtract(amount);
    }

    private void requireCurrency(Money amount) {
        if (amount.currency() != currency) {
            throw new CurrencyMismatchException(currency, amount.currency());
        }
    }

    public Long id() { return id; }
    public long merchantId() { return merchantId; }
    public Currency currency() { return currency; }
    public Money balance() { return balance; }
    public long version() { return version; }
}
