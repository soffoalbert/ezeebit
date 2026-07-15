package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.adapter.in.web.dto.AutoSettleRuleBody;
import com.ezeebit.wallet.application.port.in.ManageAutoSettleRulesUseCase;
import com.ezeebit.wallet.application.port.in.ManageAutoSettleRulesUseCase.AutoSettleRuleView;
import com.ezeebit.wallet.application.port.in.ManageAutoSettleRulesUseCase.UpsertAutoSettleRuleCommand;
import com.ezeebit.wallet.domain.model.Currency;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Task 5 — manage a merchant's auto-settle rules (one per source currency). */
@RestController
@RequestMapping("/merchants/{merchantId}/auto-settle-rules")
class AutoSettleRuleController {

    private final ManageAutoSettleRulesUseCase manageRules;

    AutoSettleRuleController(ManageAutoSettleRulesUseCase manageRules) {
        this.manageRules = manageRules;
    }

    @PutMapping("/{sourceCurrency}")
    AutoSettleRuleView upsert(@PathVariable long merchantId,
                              @PathVariable Currency sourceCurrency,
                              @Valid @RequestBody AutoSettleRuleBody body) {
        return manageRules.upsert(new UpsertAutoSettleRuleCommand(merchantId, sourceCurrency,
                body.targetCurrency(), body.percentage(), body.enabled()));
    }

    @GetMapping
    List<AutoSettleRuleView> list(@PathVariable long merchantId) {
        return manageRules.forMerchant(merchantId);
    }
}
