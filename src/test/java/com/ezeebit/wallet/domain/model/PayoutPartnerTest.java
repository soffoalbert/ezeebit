package com.ezeebit.wallet.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PayoutPartnerTest {

    private PayoutPartner za(BigDecimal limit, boolean healthy) {
        return new PayoutPartner(1L, "za-swift-eft", "SWIFT EFT", "ZA", Currency.ZAR, limit, healthy, 1);
    }

    @Test
    void eligibleWhenCountryCurrencyLimitAndHealthAllPass() {
        PayoutPartner p = za(new BigDecimal("1000.00"), true);
        assertThat(p.rejectionFor("ZA", Money.of("500.00", Currency.ZAR))).isEmpty();
    }

    @Test
    void currencyMismatchIsRejected() {
        PayoutPartner p = za(null, true);
        assertThat(p.rejectionFor("ZA", Money.of("500.00", Currency.NGN)))
                .contains(PayoutPartner.Rejection.CURRENCY_MISMATCH);
    }

    @Test
    void countryMismatchIsRejected() {
        PayoutPartner p = za(null, true);
        assertThat(p.rejectionFor("NG", Money.of("500.00", Currency.ZAR)))
                .contains(PayoutPartner.Rejection.COUNTRY_MISMATCH);
    }

    @Test
    void overLimitIsRejected() {
        PayoutPartner p = za(new BigDecimal("1000.00"), true);
        assertThat(p.rejectionFor("ZA", Money.of("1500.00", Currency.ZAR)))
                .contains(PayoutPartner.Rejection.OVER_LIMIT);
    }

    @Test
    void unhealthyIsRejectedButStructurallyEligible() {
        PayoutPartner p = za(new BigDecimal("1000.00"), false);
        Money amount = Money.of("500.00", Currency.ZAR);
        assertThat(p.rejectionFor("ZA", amount)).contains(PayoutPartner.Rejection.UNHEALTHY);
        assertThat(p.isStructurallyEligible("ZA", amount)).isTrue();   // health ignored
    }

    @Test
    void structuralRejectionTakesPrecedenceOverHealth() {
        PayoutPartner p = za(new BigDecimal("1000.00"), false);
        // Over limit AND unhealthy — the structural reason wins.
        assertThat(p.rejectionFor("ZA", Money.of("1500.00", Currency.ZAR)))
                .contains(PayoutPartner.Rejection.OVER_LIMIT);
    }

    @Test
    void nullCountryMatchesAnyCountry() {
        PayoutPartner chain = new PayoutPartner(2L, "chain-usdt", "On-chain USDT", null,
                Currency.USDT, null, true, 1);
        assertThat(chain.rejectionFor("ZA", Money.of("10.000000", Currency.USDT))).isEmpty();
        assertThat(chain.rejectionFor("NG", Money.of("10.000000", Currency.USDT))).isEmpty();
    }
}
