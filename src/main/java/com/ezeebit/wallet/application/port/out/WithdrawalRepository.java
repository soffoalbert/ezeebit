package com.ezeebit.wallet.application.port.out;

import com.ezeebit.wallet.domain.model.Withdrawal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WithdrawalRepository {

    Withdrawal save(Withdrawal withdrawal);

    Optional<Withdrawal> find(UUID id);

    Optional<Withdrawal> lockForUpdate(UUID id);

    Optional<Withdrawal> findByPayoutReference(String payoutReference);

    /** PENDING withdrawals older than {@code olderThan} — candidates for re-submission after a crash. */
    List<Withdrawal> findStalePending(Instant olderThan, int limit);
}
