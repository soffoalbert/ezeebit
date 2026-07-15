package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.out.PayoutPartnerRepository;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.PayoutPartner;
import com.ezeebit.wallet.domain.model.RoutingPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayoutRoutingServiceTest {

    private final PayoutPartnerRepository partners = mock(PayoutPartnerRepository.class);
    private final PayoutRoutingService routing = new PayoutRoutingService(partners);

    private PayoutPartner swift(boolean healthy) {
        return new PayoutPartner(1L, "za-swift-eft", "SWIFT", "ZA", Currency.ZAR,
                new BigDecimal("1000.00"), healthy, 1);
    }

    private PayoutPartner payfast(boolean healthy) {
        return new PayoutPartner(2L, "za-payfast", "PayFast", "ZA", Currency.ZAR, null, healthy, 2);
    }

    @Test
    void eligiblePartnersAreOrderedByPriority() {
        when(partners.findRoutes(eq("ZA"), eq(Currency.ZAR)))
                .thenReturn(List.of(swift(true), payfast(true)));

        RoutingPlan plan = routing.plan("ZA", Money.of("500.00", Currency.ZAR));

        assertThat(plan.eligible()).extracting(PayoutPartner::code)
                .containsExactly("za-swift-eft", "za-payfast");
        assertThat(plan.rejections()).isEmpty();
    }

    @Test
    void overLimitPartnerIsPartitionedIntoRejections() {
        when(partners.findRoutes(eq("ZA"), eq(Currency.ZAR)))
                .thenReturn(List.of(swift(true), payfast(true)));

        RoutingPlan plan = routing.plan("ZA", Money.of("5000.00", Currency.ZAR));

        // swift caps at 1000; payfast has no cap.
        assertThat(plan.eligible()).extracting(PayoutPartner::code).containsExactly("za-payfast");
        assertThat(plan.rejections()).containsEntry("za-swift-eft", PayoutPartner.Rejection.OVER_LIMIT);
    }

    @Test
    void allUnhealthyIsTransientlyUnroutable() {
        when(partners.findRoutes(eq("ZA"), eq(Currency.ZAR)))
                .thenReturn(List.of(swift(false), payfast(false)));

        RoutingPlan plan = routing.plan("ZA", Money.of("500.00", Currency.ZAR));

        assertThat(plan.hasEligible()).isFalse();
        assertThat(plan.isTransientlyUnroutable()).isTrue();
    }

    @Test
    void structuralRouteExistsIgnoresHealth() {
        when(partners.findRoutes(eq("ZA"), eq(Currency.ZAR)))
                .thenReturn(List.of(swift(false), payfast(false)));

        // Both unhealthy, but a route structurally exists (used to gate at request time).
        assertThat(routing.structuralRouteExists("ZA", Money.of("500.00", Currency.ZAR))).isTrue();
    }

    @Test
    void noPartnersMeansNoStructuralRoute() {
        when(partners.findRoutes(eq("ZA"), eq(Currency.KES))).thenReturn(List.of());
        assertThat(routing.structuralRouteExists("ZA", Money.of("500.00", Currency.KES))).isFalse();
    }
}
