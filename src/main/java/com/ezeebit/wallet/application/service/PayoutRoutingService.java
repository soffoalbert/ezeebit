package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.out.PayoutPartnerRepository;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.PayoutPartner;
import com.ezeebit.wallet.domain.model.RoutingPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Task 6 — decides which payout partner(s) a withdrawal should be routed to. This is business
 * policy (country/currency/limit/health with priority failover), kept in the application layer
 * for visibility, attribution, and testability; the {@code PayoutRail} port stays pure transport.
 */
@Service
public class PayoutRoutingService {

    private static final Logger audit = LoggerFactory.getLogger("wallet.audit");

    private final PayoutPartnerRepository partners;

    public PayoutRoutingService(PayoutPartnerRepository partners) {
        this.partners = partners;
    }

    /**
     * Partition the candidate partners for a payout into an ordered eligible list (priority
     * first) and a rejection map explaining the near-misses. Considers health.
     */
    public RoutingPlan plan(String country, Money amount) {
        List<PayoutPartner> candidates = partners.findRoutes(country, amount.currency());
        List<PayoutPartner> eligible = new ArrayList<>();
        Map<String, PayoutPartner.Rejection> rejections = new LinkedHashMap<>();
        for (PayoutPartner partner : candidates) {
            partner.rejectionFor(country, amount).ifPresentOrElse(
                    reason -> rejections.put(partner.code(), reason),
                    () -> eligible.add(partner));
        }
        audit.info("payout_routing country={} currency={} amount={} eligible={} rejected={}",
                country, amount.currency(), amount.amount().toPlainString(),
                eligible.stream().map(PayoutPartner::code).toList(), rejections);
        return new RoutingPlan(eligible, rejections);
    }

    /**
     * Whether any partner could ever serve this payout, ignoring current health. Used at request
     * time to reject a structurally impossible payout before funds are held.
     */
    public boolean structuralRouteExists(String country, Money amount) {
        return partners.findRoutes(country, amount.currency()).stream()
                .anyMatch(p -> p.isStructurallyEligible(country, amount));
    }
}
