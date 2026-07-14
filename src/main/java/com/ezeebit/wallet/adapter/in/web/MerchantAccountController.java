package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.DepositRequest;
import com.ezeebit.wallet.application.port.in.BalanceView;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.GetBalancesUseCase;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase.LedgerPage;
import com.ezeebit.wallet.domain.model.Currency;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Task 1 — hold balances. Deposit funds, read balances, and read the audit history
 * that explains how a balance reached its value.
 */
@RestController
@RequestMapping("/merchants/{merchantId}")
class MerchantAccountController {

    private final DepositFundsUseCase depositFunds;
    private final GetBalancesUseCase getBalances;
    private final GetLedgerHistoryUseCase getLedger;

    MerchantAccountController(DepositFundsUseCase depositFunds, GetBalancesUseCase getBalances,
                              GetLedgerHistoryUseCase getLedger) {
        this.depositFunds = depositFunds;
        this.getBalances = getBalances;
        this.getLedger = getLedger;
    }

    @PostMapping("/deposits")
    BalanceView deposit(@PathVariable long merchantId,
                        @RequestHeader("Idempotency-Key") String idempotencyKey,
                        @Valid @RequestBody DepositRequest body) {
        return depositFunds.deposit(new DepositFundsUseCase.DepositCommand(
                merchantId, body.currency(), body.amount(), idempotencyKey, body.reference()));
    }

    @GetMapping("/balances")
    List<BalanceView> balances(@PathVariable long merchantId) {
        return getBalances.balancesOf(merchantId);
    }

    @GetMapping("/accounts/{currency}/ledger")
    LedgerPage ledger(@PathVariable long merchantId,
                      @PathVariable Currency currency,
                      @RequestParam(required = false) Long before,
                      @RequestParam(defaultValue = "50") int limit) {
        return getLedger.history(merchantId, currency, before, limit);
    }
}
