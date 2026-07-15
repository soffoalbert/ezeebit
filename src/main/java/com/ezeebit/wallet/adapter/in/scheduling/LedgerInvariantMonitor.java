package com.ezeebit.wallet.adapter.in.scheduling;

import com.ezeebit.wallet.application.port.out.LedgerIntegrityChecker;
import com.ezeebit.wallet.application.port.out.LedgerIntegrityChecker.BalanceDrift;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Periodically checks the wallet's core invariant — that every account's cached balance
 * equals the sum of its ledger entries — and raises an alert (ERROR log + a metric) if it
 * is ever violated. A non-zero {@code wallet.ledger.invariant.violations} gauge is the
 * signal that something has corrupted the ledger and needs immediate investigation.
 */
@Component
class LedgerInvariantMonitor {

    private static final Logger log = LoggerFactory.getLogger(LedgerInvariantMonitor.class);

    private final LedgerIntegrityChecker checker;
    private final AtomicInteger violations;

    LedgerInvariantMonitor(LedgerIntegrityChecker checker, MeterRegistry meters) {
        this.checker = checker;
        this.violations = meters.gauge("wallet.ledger.invariant.violations", new AtomicInteger(0));
    }

    @Scheduled(fixedDelayString = "${wallet.ledger.invariant-check-ms:300000}",
               initialDelayString = "${wallet.ledger.invariant-check-ms:300000}")
    void check() {
        List<BalanceDrift> drift = checker.findDrift(100);
        violations.set(drift.size());
        if (!drift.isEmpty()) {
            for (BalanceDrift d : drift) {
                log.error("LEDGER INVARIANT VIOLATION: account {} balance={} ledgerSum={} diff={}",
                        d.accountId(), d.balance(), d.ledgerSum(), d.difference());
            }
        }
    }
}
