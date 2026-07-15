package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.IncomingPaymentNotification;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase;
import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase.NotifyIncomingPaymentCommand;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 4 — webhook the blockchain confirmation service calls as an incoming payment gains
 * confirmations. Idempotent on (txHash, outputIndex): duplicate/out-of-order notifications are
 * safe no-ops (204), a contradictory replay is a 409.
 */
@RestController
@RequestMapping("/internal/incoming-payments")
class IncomingPaymentCallbackController {

    private final NotifyIncomingPaymentUseCase notifyIncomingPayment;

    IncomingPaymentCallbackController(NotifyIncomingPaymentUseCase notifyIncomingPayment) {
        this.notifyIncomingPayment = notifyIncomingPayment;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void notify(@Valid @RequestBody IncomingPaymentNotification body) {
        notifyIncomingPayment.notify(new NotifyIncomingPaymentCommand(
                body.merchantId(), body.txHash(), body.outputIndex(),
                body.currency(), body.amount(), body.confirmations()));
    }
}
