package com.ezeebit.wallet.application.port.out;

/**
 * Read-only lookup of merchant reference data needed for routing (Task 6). Kept deliberately
 * thin — the wallet owns balances, not the merchant record — so this exposes only the country
 * used to choose a payout partner.
 */
public interface MerchantDirectory {

    /** ISO-3166 alpha-2 country of the merchant, or {@code null} if unknown. */
    String countryOf(long merchantId);
}
