package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.WithdrawalRequestBody;
import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.WithdrawalView;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Task 3 — withdraw funds. Requesting a payout holds the funds immediately and returns
 * a PENDING withdrawal; settlement happens asynchronously. The Idempotency-Key header
 * makes duplicate submissions (the flaky-mobile double-tap) safe.
 */
@RestController
@RequestMapping("/merchants/{merchantId}/withdrawals")
class WithdrawalController {

    private final RequestWithdrawalUseCase requestWithdrawal;
    private final GetWithdrawalUseCase getWithdrawal;
    private final ObjectMapper objectMapper;

    WithdrawalController(RequestWithdrawalUseCase requestWithdrawal,
                         GetWithdrawalUseCase getWithdrawal, ObjectMapper objectMapper) {
        this.requestWithdrawal = requestWithdrawal;
        this.getWithdrawal = getWithdrawal;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    WithdrawalView request(@PathVariable long merchantId,
                           @RequestHeader("Idempotency-Key") String idempotencyKey,
                           @Valid @RequestBody WithdrawalRequestBody body) {
        String destination = body.destination().toString();   // canonical JSON string
        return requestWithdrawal.request(new RequestWithdrawalUseCase.RequestWithdrawalCommand(
                merchantId, body.currency(), body.amount(), destination, idempotencyKey));
    }

    @GetMapping("/{withdrawalId}")
    WithdrawalView get(@PathVariable long merchantId, @PathVariable String withdrawalId) {
        return getWithdrawal.get(merchantId, withdrawalId);
    }
}
