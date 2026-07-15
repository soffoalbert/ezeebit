package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.AutoSettleRule;
import com.ezeebit.wallet.domain.model.Currency;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface ManageAutoSettleRulesUseCase {

    AutoSettleRuleView upsert(UpsertAutoSettleRuleCommand command);

    List<AutoSettleRuleView> forMerchant(long merchantId);

    record UpsertAutoSettleRuleCommand(long merchantId, Currency sourceCurrency,
                                       Currency targetCurrency, BigDecimal percentage, boolean enabled) {}

    record AutoSettleRuleView(long merchantId, Currency sourceCurrency, Currency targetCurrency,
                              BigDecimal percentage, boolean enabled, Instant updatedAt) {

        public static AutoSettleRuleView of(AutoSettleRule r) {
            return new AutoSettleRuleView(r.merchantId(), r.sourceCurrency(), r.targetCurrency(),
                    r.percentage(), r.enabled(), r.updatedAt());
        }
    }
}
