package com.ezeebit.wallet.application.service;

import com.ezeebit.wallet.application.port.in.ExecuteConversionUseCase;
import com.ezeebit.wallet.application.port.in.QuoteConversionUseCase;
import com.ezeebit.wallet.application.port.out.ConversionRepository;
import com.ezeebit.wallet.application.port.out.ExchangeRateProvider;
import com.ezeebit.wallet.application.port.out.QuoteRepository;
import com.ezeebit.wallet.application.service.support.RequestHash;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.exception.InvalidRateException;
import com.ezeebit.wallet.domain.exception.QuoteNotFoundException;
import com.ezeebit.wallet.domain.model.Conversion;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.LedgerEntryType;
import com.ezeebit.wallet.domain.model.Money;
import com.ezeebit.wallet.domain.model.Quote;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Task 2 — convert one currency into another via a quote → execute flow.
 *
 * <p>The merchant is quoted an exact rate (mid-market minus a spread in the platform's
 * favour) that is locked for a short TTL. They execute against that quoted rate, so they
 * never suffer surprise slippage, while the TTL bounds the platform's exposure to a
 * moving market. A bad rate from the feed is rejected before it can be quoted.
 */
@Service
public class ConversionApplicationService implements QuoteConversionUseCase, ExecuteConversionUseCase {

    private static final String ENDPOINT = "conversion";

    private final ExchangeRateProvider rateProvider;
    private final QuoteRepository quotes;
    private final ConversionRepository conversions;
    private final LedgerPostingService posting;
    private final IdempotencyGuard idempotency;
    private final WalletProperties properties;
    private final Clock clock;

    // Last rate we accepted per pair, used only to reject wildly-off feed values.
    // In-memory and best-effort; a shared cache would be needed across instances.
    private final ConcurrentHashMap<String, BigDecimal> lastAcceptedRate = new ConcurrentHashMap<>();

    public ConversionApplicationService(ExchangeRateProvider rateProvider, QuoteRepository quotes,
                                        ConversionRepository conversions, LedgerPostingService posting,
                                        IdempotencyGuard idempotency, WalletProperties properties,
                                        Clock clock) {
        this.rateProvider = rateProvider;
        this.quotes = quotes;
        this.conversions = conversions;
        this.posting = posting;
        this.idempotency = idempotency;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public QuoteView quote(QuoteCommand command) {
        if (command.fromCurrency() == command.toCurrency()) {
            throw new IllegalArgumentException("cannot convert a currency into itself");
        }
        Money fromAmount = Money.of(command.fromAmount(), command.fromCurrency());
        if (!fromAmount.isPositive()) {
            throw new IllegalArgumentException("conversion amount must be positive");
        }

        BigDecimal midRate = fetchAndValidateRate(command.fromCurrency(), command.toCurrency());

        // Spread taken in the platform's favour: the merchant receives slightly less.
        BigDecimal spread = properties.conversion().spread();
        BigDecimal effectiveRate = midRate.multiply(BigDecimal.ONE.subtract(spread));

        // Round the amount the merchant receives DOWN, so rounding never favours them.
        BigDecimal toRaw = fromAmount.amount().multiply(effectiveRate)
                .setScale(command.toCurrency().scale(), RoundingMode.DOWN);
        Money toAmount = Money.of(toRaw, command.toCurrency());

        Instant now = clock.instant();
        Quote quote = new Quote(UUID.randomUUID(), command.merchantId(), fromAmount, toAmount,
                effectiveRate, Quote.Status.ACTIVE, now,
                now.plusSeconds(properties.conversion().quoteTtlSeconds()));
        quotes.save(quote);

        return new QuoteView(quote.id().toString(), command.fromCurrency(), command.toCurrency(),
                fromAmount.amount(), toAmount.amount(), effectiveRate, quote.expiresAt());
    }

    @Override
    @Transactional
    public ConversionView execute(ExecuteConversionCommand command) {
        UUID quoteId = parseQuoteId(command.quoteId());
        String hash = RequestHash.of(command.merchantId(), command.quoteId());

        return idempotency.execute(command.merchantId(), ENDPOINT, command.idempotencyKey(), hash,
                ConversionView.class, () -> doExecute(command.merchantId(), quoteId));
    }

    private ConversionView doExecute(long merchantId, UUID quoteId) {
        Quote quote = quotes.lockForUpdate(quoteId)
                .orElseThrow(() -> new QuoteNotFoundException(quoteId));
        if (quote.merchantId() != merchantId) {
            // Do not reveal another merchant's quote.
            throw new QuoteNotFoundException(quoteId);
        }

        Instant now = clock.instant();
        quote.consume(now);   // enforces single-use + not-expired (state machine)

        UUID operationId = UUID.randomUUID();
        // Debit the source first (may throw InsufficientFunds), then credit the target.
        posting.post(merchantId, LedgerEntryType.CONVERSION_OUT, quote.fromAmount(), operationId,
                "conversion " + quoteId);
        posting.post(merchantId, LedgerEntryType.CONVERSION_IN, quote.toAmount(), operationId,
                "conversion " + quoteId);

        quotes.save(quote);
        Conversion conversion = Conversion.executed(merchantId, quote, operationId, now);
        conversions.save(conversion);

        return new ConversionView(conversion.id().toString(), quoteId.toString(),
                conversion.status().name(), quote.fromAmount().currency(), quote.toAmount().currency(),
                quote.fromAmount().amount(), quote.toAmount().amount(), quote.rate(), now);
    }

    private BigDecimal fetchAndValidateRate(Currency from, Currency to) {
        ExchangeRateProvider.Rate rate = rateProvider.getRate(from, to);   // may throw RateUnavailable
        BigDecimal value = rate.rate();
        if (value == null || value.signum() <= 0) {
            throw new InvalidRateException("exchange rate for " + from + "->" + to + " was non-positive");
        }

        String pair = from + ":" + to;
        BigDecimal previous = lastAcceptedRate.get(pair);
        if (previous != null) {
            BigDecimal deviation = value.subtract(previous).abs()
                    .divide(previous, 8, RoundingMode.HALF_UP);
            if (deviation.compareTo(properties.conversion().maxRateDeviation()) > 0) {
                throw new InvalidRateException("exchange rate for " + pair + " moved " + deviation
                        + ", exceeding the safety threshold; refusing to quote");
            }
        }
        lastAcceptedRate.put(pair, value);
        return value;
    }

    private UUID parseQuoteId(String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw new QuoteNotFoundException(null);
        }
    }
}
