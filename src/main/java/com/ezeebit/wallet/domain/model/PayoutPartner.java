package com.ezeebit.wallet.domain.model;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * A payout partner route (Task 6): one settlement channel for a specific currency, optionally
 * scoped to a country and a per-transaction limit, with a health flag and a failover priority.
 *
 * <p>A payout can be routed to this partner only if it clears every check in
 * {@link #rejectionFor}. The structural checks (currency, country, limit) describe whether the
 * partner <em>can ever</em> serve the payout; health is a transient, operational gate handled
 * separately so routing can distinguish "no route exists" from "the route is temporarily down".
 */
public record PayoutPartner(Long id, String code, String name, String country, Currency currency,
                            BigDecimal perTxLimit, boolean healthy, int priority) {

    public enum Rejection { CURRENCY_MISMATCH, COUNTRY_MISMATCH, OVER_LIMIT, UNHEALTHY }

    public PayoutPartner {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(currency, "currency");
    }

    /**
     * Why this partner cannot serve a payout of {@code amount} for a merchant in
     * {@code merchantCountry}, or empty if it is fully eligible. Structural reasons take
     * precedence over health, so a partner that could never serve the payout is never reported
     * merely as "unhealthy".
     */
    public Optional<Rejection> rejectionFor(String merchantCountry, Money amount) {
        Optional<Rejection> structural = structuralRejection(merchantCountry, amount);
        if (structural.isPresent()) {
            return structural;
        }
        return healthy ? Optional.empty() : Optional.of(Rejection.UNHEALTHY);
    }

    /** The structural (currency/country/limit) reason this partner cannot serve the payout, ignoring health. */
    public Optional<Rejection> structuralRejection(String merchantCountry, Money amount) {
        if (currency != amount.currency()) {
            return Optional.of(Rejection.CURRENCY_MISMATCH);
        }
        if (country != null && !country.equals(merchantCountry)) {
            return Optional.of(Rejection.COUNTRY_MISMATCH);
        }
        if (perTxLimit != null && amount.amount().compareTo(perTxLimit) > 0) {
            return Optional.of(Rejection.OVER_LIMIT);
        }
        return Optional.empty();
    }

    public boolean isEligible(String merchantCountry, Money amount) {
        return rejectionFor(merchantCountry, amount).isEmpty();
    }

    public boolean isStructurallyEligible(String merchantCountry, Money amount) {
        return structuralRejection(merchantCountry, amount).isEmpty();
    }
}
