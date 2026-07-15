package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.PayoutPartner;

import java.util.List;
import java.util.Optional;

public interface PayoutPartnerRepository {

    /**
     * Candidate partners for a country + currency, ordered by priority (health-unfiltered — the
     * routing service decides what to do with unhealthy partners). Includes country-agnostic
     * partners (those with a NULL country).
     */
    List<PayoutPartner> findRoutes(String country, Currency currency);

    Optional<PayoutPartner> findByCode(String code);

    List<PayoutPartner> findAll();

    /** Flip a partner's health flag. Returns false if no partner has that code. */
    boolean setHealthy(String code, boolean healthy);
}
