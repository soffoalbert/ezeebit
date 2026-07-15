package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.application.port.in.BalanceView;
import com.ezeebit.wallet.application.port.in.DepositFundsUseCase;
import com.ezeebit.wallet.application.port.in.GetBalancesUseCase;
import com.ezeebit.wallet.application.port.in.GetLedgerHistoryUseCase;
import com.ezeebit.wallet.domain.model.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MerchantAccountController.class)
class MerchantAccountControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    DepositFundsUseCase depositFunds;
    @MockBean
    GetBalancesUseCase getBalances;
    @MockBean
    GetLedgerHistoryUseCase getLedger;

    @Test
    void depositReturnsBalance() throws Exception {
        when(depositFunds.deposit(any()))
                .thenReturn(new BalanceView(1L, Currency.ZAR, new BigDecimal("100.00"), BigDecimal.ZERO));

        mvc.perform(post("/merchants/1/deposits")
                        .header("Idempotency-Key", "dep-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"ZAR\",\"amount\":\"100.00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ZAR"))
                .andExpect(jsonPath("$.balance").value(100.00))
                .andExpect(jsonPath("$.pendingIncoming").value(0));
    }

    @Test
    void missingIdempotencyKeyIsRejected() throws Exception {
        mvc.perform(post("/merchants/1/deposits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"ZAR\",\"amount\":\"100.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void negativeAmountFailsValidation() throws Exception {
        mvc.perform(post("/merchants/1/deposits")
                        .header("Idempotency-Key", "dep-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"ZAR\",\"amount\":\"-5.00\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
