package com.ezeebit.wallet.application.service.support;

import com.ezeebit.wallet.application.port.out.ExchangeRateProvider;
import com.ezeebit.wallet.application.port.out.RateReferenceStore;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.exception.InvalidRateException;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.Optional;

/**
 * Shared pricing for currency conversions — used both by merchant quotes (Task 2) and by
 * auto-settle (Task 5). Fetches the current market rate, rejects a non-positive or wildly
 * deviating rate (the safety guard against a bad feed), records the observation, applies the
 * platform spread, and rounds the merchant's receive amount DOWN.
 */
@Component
public class ConversionPricer {

    private final ExchangeRateProvider rateProvider;
    private final RateReferenceStore rateReference;
    private final WalletProperties properties;
    private final Clock clock;

    public ConversionPricer(ExchangeRateProvider rateProvider, RateReferenceStore rateReference,
                            WalletProperties properties, Clock clock) {
        this.rateProvider = rateProvider;
        this.rateReference = rateReference;
        this.properties = properties;
        this.clock = clock;
    }

    /** The result of pricing a conversion at the current market rate. */
    public record PricedConversion(BigDecimal midRate, BigDecimal effectiveRate, Money toAmount) {}

    /**
     * Price {@code fromAmount} in {@code from} into {@code to} at the current market rate.
     * May throw {@code RateUnavailableException} (feed down) or {@link InvalidRateException}
     * (non-positive or deviating rate); callers translate those into retries or 4xx/5xx.
     */
    public PricedConversion price(Currency from, Currency to, Money fromAmount) {
        BigDecimal midRate = fetchAndValidateRate(from, to);

        // Spread taken in the platform's favour: the merchant receives slightly less.
        BigDecimal spread = properties.conversion().spread();
        BigDecimal effectiveRate = midRate.multiply(BigDecimal.ONE.subtract(spread));

        // Round the amount the merchant receives DOWN, so rounding never favours them.
        BigDecimal toRaw = fromAmount.amount().multiply(effectiveRate)
                .setScale(to.scale(), RoundingMode.DOWN);
        return new PricedConversion(midRate, effectiveRate, Money.of(toRaw, to));
    }

    private BigDecimal fetchAndValidateRate(Currency from, Currency to) {
        ExchangeRateProvider.Rate rate = rateProvider.getRate(from, to);   // may throw RateUnavailable
        BigDecimal value = rate.rate();
        if (value == null || value.signum() <= 0) {
            throw new InvalidRateException("exchange rate for " + from + "->" + to + " was non-positive");
        }

        Optional<BigDecimal> previous = rateReference.lastRate(from, to);
        if (previous.isPresent()) {
            BigDecimal deviation = value.subtract(previous.get()).abs()
                    .divide(previous.get(), 8, RoundingMode.HALF_UP);
            if (deviation.compareTo(properties.conversion().maxRateDeviation()) > 0) {
                throw new InvalidRateException("exchange rate for " + from + ":" + to + " moved "
                        + deviation + ", exceeding the safety threshold; refusing to quote");
            }
        }
        rateReference.record(from, to, value, clock.instant());
        return value;
    }
}
