package com.ezeebit.wallet.adapter.in.scheduling;

import com.ezeebit.wallet.application.port.out.QuoteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Expires quotes whose TTL has passed. Execution already refuses an expired quote at the
 * point of use; this job keeps the stored state honest so {@code ACTIVE} means "still
 * usable" for reporting and reconciliation.
 */
@Component
class QuoteExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(QuoteExpiryJob.class);

    private final QuoteRepository quotes;
    private final Clock clock;

    QuoteExpiryJob(QuoteRepository quotes, Clock clock) {
        this.quotes = quotes;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wallet.conversion.expiry-sweep-ms:60000}",
               initialDelayString = "${wallet.conversion.expiry-sweep-ms:60000}")
    @Transactional
    public void expireStaleQuotes() {
        int expired = quotes.expireActiveOlderThan(clock.instant());
        if (expired > 0) {
            log.debug("expired {} stale ACTIVE quote(s)", expired);
        }
    }
}
