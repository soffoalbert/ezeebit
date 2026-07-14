package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Account;
import com.ezeebit.wallet.domain.model.Currency;

import java.util.List;
import java.util.Optional;

public interface AccountRepository {

    /**
     * Load the account for update, taking a pessimistic row lock. This is the
     * serialization point that keeps concurrent deposits, conversions, and
     * withdrawals on the same account from racing. Must be called inside a
     * transaction.
     */
    Optional<Account> lockForUpdate(long merchantId, Currency currency);

    Optional<Account> find(long merchantId, Currency currency);

    List<Account> findAllForMerchant(long merchantId);

    Account save(Account account);
}
