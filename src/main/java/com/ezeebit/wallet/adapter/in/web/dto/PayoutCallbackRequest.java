package com.ezeebit.wallet.adapter.in.web.dto;

import com.ezeebit.wallet.application.port.in.HandlePayoutResultUseCase.PayoutResult.Outcome;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PayoutCallbackRequest(
        @NotBlank String payoutReference,
        @NotNull Outcome outcome,
        String reason) {
}
