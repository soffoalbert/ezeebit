package com.ezeebit.wallet.domain.model;

import java.util.List;
import java.util.Map;

/**
 * The outcome of routing a payout (Task 6): the ordered list of eligible partners to try
 * (priority first) and, for observability, why each considered-but-rejected partner was
 * excluded. An empty {@code eligible} list with only {@link PayoutPartner.Rejection#UNHEALTHY}
 * rejections means the route exists but is temporarily down (retry); any other emptiness means
 * no usable route (terminal).
 */
public record RoutingPlan(List<PayoutPartner> eligible,
                          Map<String, PayoutPartner.Rejection> rejections) {

    public boolean hasEligible() {
        return !eligible.isEmpty();
    }

    /** True when nothing is eligible but every rejection was transient (unhealthy). */
    public boolean isTransientlyUnroutable() {
        return eligible.isEmpty()
                && !rejections.isEmpty()
                && rejections.values().stream().allMatch(r -> r == PayoutPartner.Rejection.UNHEALTHY);
    }
}
