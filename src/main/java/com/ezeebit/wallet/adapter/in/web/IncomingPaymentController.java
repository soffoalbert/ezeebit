package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.application.port.in.GetIncomingPaymentsUseCase;
import com.ezeebit.wallet.application.port.in.GetIncomingPaymentsUseCase.IncomingPaymentView;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Task 4 — list a merchant's incoming payments (pending and confirmed). */
@RestController
@RequestMapping("/merchants/{merchantId}/incoming-payments")
class IncomingPaymentController {

    private final GetIncomingPaymentsUseCase getIncomingPayments;

    IncomingPaymentController(GetIncomingPaymentsUseCase getIncomingPayments) {
        this.getIncomingPayments = getIncomingPayments;
    }

    @GetMapping
    List<IncomingPaymentView> list(@PathVariable long merchantId) {
        return getIncomingPayments.forMerchant(merchantId);
    }
}
