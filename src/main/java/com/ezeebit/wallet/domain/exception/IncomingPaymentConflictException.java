package com.ezeebit.wallet.domain.exception;

/**
 * A confirmation notification arrived for a (tx_hash, output_index) already recorded, but
 * its immutable facts (merchant, currency, or amount) disagree with what was first seen —
 * a genuine conflict, not a benign duplicate. Surfaced as 409.
 */
public class IncomingPaymentConflictException extends WalletException {
    public IncomingPaymentConflictException(String txHash, int outputIndex) {
        super("INCOMING_PAYMENT_CONFLICT",
                "incoming payment " + txHash + ":" + outputIndex
                        + " was already recorded with different details");
    }
}
