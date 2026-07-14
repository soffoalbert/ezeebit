package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.PayoutCallbackRequest;
import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase;
import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase.PayoutResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Webhook the payout rail calls when a payout settles. Idempotent: duplicate callbacks
 * for the same reference are no-ops. The in-process stub rail also drives this same use
 * case directly, so the HTTP path and the stub path are equivalent.
 */
@RestController
@RequestMapping("/internal/payout-callbacks")
class PayoutCallbackController {

    private final HandlePayoutResultUseCase handlePayoutResult;

    PayoutCallbackController(HandlePayoutResultUseCase handlePayoutResult) {
        this.handlePayoutResult = handlePayoutResult;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void callback(@Valid @RequestBody PayoutCallbackRequest body) {
        handlePayoutResult.handle(new PayoutResult(
                body.payoutReference(), body.outcome(), body.reason()));
    }
}
