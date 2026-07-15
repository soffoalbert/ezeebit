package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.ManagePayoutPartnersUseCase;
import com.ezeebit.wallet.application.port.out.PayoutPartnerRepository;
import com.ezeebit.wallet.domain.exception.PayoutPartnerNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Task 6 — admin view and health control over the payout partner registry. */
@Service
public class PayoutPartnerAdminService implements ManagePayoutPartnersUseCase {

    private final PayoutPartnerRepository partners;

    public PayoutPartnerAdminService(PayoutPartnerRepository partners) {
        this.partners = partners;
    }

    @Override
    public List<PayoutPartnerView> list() {
        return partners.findAll().stream().map(PayoutPartnerView::of).toList();
    }

    @Override
    public void setHealthy(String code, boolean healthy) {
        if (!partners.setHealthy(code, healthy)) {
            throw new PayoutPartnerNotFoundException(code);
        }
    }
}
