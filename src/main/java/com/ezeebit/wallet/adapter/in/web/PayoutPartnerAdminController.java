package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.PayoutPartnerHealthRequest;
import com.ezeebit.wallet.application.port.in.ManagePayoutPartnersUseCase;
import com.ezeebit.wallet.application.port.in.ManagePayoutPartnersUseCase.PayoutPartnerView;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Task 6 — inspect the payout partner registry and toggle a partner's health (demos failover). */
@RestController
@RequestMapping("/internal/payout-partners")
class PayoutPartnerAdminController {

    private final ManagePayoutPartnersUseCase managePartners;

    PayoutPartnerAdminController(ManagePayoutPartnersUseCase managePartners) {
        this.managePartners = managePartners;
    }

    @GetMapping
    List<PayoutPartnerView> list() {
        return managePartners.list();
    }

    @PatchMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void setHealth(@PathVariable String code, @Valid @RequestBody PayoutPartnerHealthRequest body) {
        managePartners.setHealthy(code, body.healthy());
    }
}
