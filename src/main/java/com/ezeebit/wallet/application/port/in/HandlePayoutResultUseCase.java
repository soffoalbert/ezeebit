package com.ezeebit.wallet.application.port.in;

public interface HandlePayoutResultUseCase {

    /** Called by the payout rail (via webhook or the stub) when a payout settles. */
    void handle(PayoutResult result);

    record PayoutResult(String payoutReference, Outcome outcome, String reason) {
        public enum Outcome { SUCCEEDED, FAILED }
    }
}
