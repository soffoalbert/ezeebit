package com.ezeebit.wallet.application.port.in;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.PayoutPartner;

import java.math.BigDecimal;
import java.util.List;

/** Task 6 — admin view and health control over the payout partner registry. */
public interface ManagePayoutPartnersUseCase {

    List<PayoutPartnerView> list();

    /** Set a partner's health flag. Throws PayoutPartnerNotFoundException for an unknown code. */
    void setHealthy(String code, boolean healthy);

    record PayoutPartnerView(String code, String name, String country, Currency currency,
                             BigDecimal perTxLimit, boolean healthy, int priority) {

        public static PayoutPartnerView of(PayoutPartner p) {
            return new PayoutPartnerView(p.code(), p.name(), p.country(), p.currency(),
                    p.perTxLimit(), p.healthy(), p.priority());
        }
    }
}
