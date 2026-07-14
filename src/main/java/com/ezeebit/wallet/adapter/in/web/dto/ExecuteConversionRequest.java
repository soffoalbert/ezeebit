package com.ezeebit.wallet.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;

public record ExecuteConversionRequest(@NotBlank String quoteId) {
}
