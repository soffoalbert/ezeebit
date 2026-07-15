package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.application.port.in.ManageAutoSettleRulesUseCase;
import com.ezeebit.wallet.application.port.in.ManageAutoSettleRulesUseCase.AutoSettleRuleView;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AutoSettleRuleController.class)
class AutoSettleRuleControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ManageAutoSettleRulesUseCase manageRules;

    @Test
    void upsertReturnsTheRule() throws Exception {
        when(manageRules.upsert(any())).thenReturn(new AutoSettleRuleView(
                1L, Currency.USDT, Currency.ZAR, new BigDecimal("50.0000"), true, Instant.now()));

        mvc.perform(put("/merchants/1/auto-settle-rules/USDT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetCurrency\":\"ZAR\",\"percentage\":\"50.0\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sourceCurrency").value("USDT"))
                .andExpect(jsonPath("$.targetCurrency").value("ZAR"));
    }

    @Test
    void invalidPercentageIsRejected() throws Exception {
        mvc.perform(put("/merchants/1/auto-settle-rules/USDT")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetCurrency\":\"ZAR\",\"percentage\":\"150.0\",\"enabled\":true}"))
                .andExpect(status().isBadRequest());
    }
}
