package com.ezeebit.wallet.adapter.out.exchangerate;

import com.ezeebit.wallet.application.port.out.ExchangeRateProvider;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.exception.RateUnavailableException;
import com.ezeebit.wallet.domain.model.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Stands in for the real exchange-rate feed. It returns a plausible mid-market rate
 * with small random jitter, and can be configured to inject latency and failures
 * ({@code wallet.exchange-rate.*}) so the conversion flow's timeout/bad-rate handling
 * can be exercised without a real dependency.
 */
@Component
class StubExchangeRateProvider implements ExchangeRateProvider {

    private static final Logger log = LoggerFactory.getLogger(StubExchangeRateProvider.class);

    // Indicative units of `to` per 1 unit of `from`. Only a few pairs are seeded;
    // the rest are derived from these or via USD as a bridge.
    private static final Map<String, BigDecimal> BASE_RATES = Map.of(
            "USDT:ZAR", new BigDecimal("18.20"),
            "USDT:NGN", new BigDecimal("1550.00"),
            "USDT:KES", new BigDecimal("129.00"),
            "USDC:ZAR", new BigDecimal("18.19"),
            "USDC:NGN", new BigDecimal("1549.00"),
            "USDC:KES", new BigDecimal("128.90")
    );

    private final WalletProperties properties;
    private final Clock clock;

    StubExchangeRateProvider(WalletProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Rate getRate(Currency from, Currency to) {
        simulateLatency();
        simulateFailure(from, to);

        BigDecimal base = resolveBase(from, to);
        // +/- 0.2% jitter to mimic a moving market.
        double jitterPct = ThreadLocalRandom.current().nextDouble(-0.002, 0.002);
        BigDecimal jittered = base.multiply(BigDecimal.valueOf(1 + jitterPct))
                .setScale(18, RoundingMode.HALF_UP);
        return new Rate(from, to, jittered, clock.instant());
    }

    private BigDecimal resolveBase(Currency from, Currency to) {
        BigDecimal direct = BASE_RATES.get(from + ":" + to);
        if (direct != null) {
            return direct;
        }
        BigDecimal inverse = BASE_RATES.get(to + ":" + from);
        if (inverse != null) {
            return BigDecimal.ONE.divide(inverse, 18, RoundingMode.HALF_UP);
        }
        // Fiat <-> fiat via a USDT bridge (e.g. ZAR -> NGN).
        BigDecimal fromToUsd = BASE_RATES.get("USDT:" + from);   // to==from side
        BigDecimal toFromUsd = BASE_RATES.get("USDT:" + to);
        if (fromToUsd != null && toFromUsd != null) {
            return toFromUsd.divide(fromToUsd, 18, RoundingMode.HALF_UP);
        }
        throw new RateUnavailableException("no rate available for " + from + "->" + to);
    }

    private void simulateLatency() {
        long latency = properties.exchangeRate().latencyMs();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RateUnavailableException("interrupted while fetching rate");
            }
        }
    }

    private void simulateFailure(Currency from, Currency to) {
        double failureRate = properties.exchangeRate().failureRate();
        if (failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate) {
            log.warn("stub exchange-rate feed simulating failure for {}->{}", from, to);
            throw new RateUnavailableException("exchange-rate feed timed out for " + from + "->" + to);
        }
    }

    Instant now() {
        return clock.instant();
    }
}
