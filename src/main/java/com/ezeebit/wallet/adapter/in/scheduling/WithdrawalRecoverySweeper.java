package com.ezeebit.wallet.adapter.in.scheduling;

import com.ezeebit.wallet.application.port.in.SubmitWithdrawalUseCase;
import com.ezeebit.wallet.application.port.out.WithdrawalRepository;
import com.ezeebit.wallet.config.WalletProperties;
import com.ezeebit.wallet.domain.model.Withdrawal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Catches withdrawals that were held but never submitted — e.g. the process crashed
 * between committing the hold and submitting to the rail. Re-submission is safe because
 * the rail call is idempotent on the withdrawal id and {@code submit} skips anything no
 * longer PENDING.
 */
@Component
class WithdrawalRecoverySweeper {

    private static final Logger log = LoggerFactory.getLogger(WithdrawalRecoverySweeper.class);
    private static final Duration STALE_AFTER = Duration.ofSeconds(30);
    private static final int BATCH = 50;

    private final WithdrawalRepository withdrawals;
    private final SubmitWithdrawalUseCase submitWithdrawal;
    private final WalletProperties properties;
    private final Clock clock;

    WithdrawalRecoverySweeper(WithdrawalRepository withdrawals,
                              SubmitWithdrawalUseCase submitWithdrawal,
                              WalletProperties properties, Clock clock) {
        this.withdrawals = withdrawals;
        this.submitWithdrawal = submitWithdrawal;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${wallet.payout.sweeper-interval-ms:15000}",
               initialDelayString = "${wallet.payout.sweeper-interval-ms:15000}")
    void resubmitStalePending() {
        Instant now = clock.instant();
        Instant deadline = now.minusMillis(properties.payout().pendingDeadlineMs());
        List<Withdrawal> stale = withdrawals.findStalePending(now.minus(STALE_AFTER), BATCH);
        for (Withdrawal w : stale) {
            try {
                if (w.createdAt().isBefore(deadline)) {
                    // Held too long with no partner able to accept it: fail it and release the
                    // funds so money is never stuck, instead of retrying forever.
                    log.warn("recovery sweeper failing expired PENDING withdrawal {}", w.id());
                    submitWithdrawal.failExpired(w.id());
                } else {
                    log.info("recovery sweeper re-submitting stale PENDING withdrawal {}", w.id());
                    submitWithdrawal.submit(w.id());
                }
            } catch (RuntimeException e) {
                log.warn("sweeper could not process {}: {}", w.id(), e.getMessage());
            }
        }
    }
}
