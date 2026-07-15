package com.ezeebit.wallet.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;

/** Admin toggle of a payout partner's health (Task 6). */
public record PayoutPartnerHealthRequest(@NotNull Boolean healthy) {
}
