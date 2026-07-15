package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.AutoSettleRule;
import com.ezeebit.wallet.domain.model.Currency;

import java.util.List;
import java.util.Optional;

public interface AutoSettleRuleRepository {

    Optional<AutoSettleRule> find(long merchantId, Currency sourceCurrency);

    List<AutoSettleRule> findAllForMerchant(long merchantId);

    /** Insert or update the rule for (merchant, sourceCurrency), returning the persisted state. */
    AutoSettleRule upsert(AutoSettleRule rule);
}
