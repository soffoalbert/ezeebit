package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.application.port.in.GetWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase;
import com.ezeebit.wallet.application.port.in.RequestWithdrawalUseCase.WithdrawalView;
import com.ezeebit.wallet.domain.exception.WithdrawalLimitExceededException;
import com.ezeebit.wallet.domain.model.Currency;
import com.ezeebit.wallet.domain.model.Money;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WithdrawalController.class)
class WithdrawalControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    RequestWithdrawalUseCase requestWithdrawal;
    @MockBean
    GetWithdrawalUseCase getWithdrawal;

    @Test
    void withdrawalIsAcceptedAsPending() throws Exception {
        when(requestWithdrawal.request(any())).thenReturn(new WithdrawalView(
                "w-1", 1L, Currency.USDT, new BigDecimal("200.000000"), "PENDING",
                null, null, Instant.now(), Instant.now()));

        mvc.perform(post("/merchants/1/withdrawals")
                        .header("Idempotency-Key", "wd-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USDT\",\"amount\":\"200.000000\"," +
                                "\"destination\":{\"address\":\"TXYZ0000000000000\"}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void overLimitWithdrawalIsUnprocessable() throws Exception {
        when(requestWithdrawal.request(any())).thenThrow(new WithdrawalLimitExceededException(
                Money.of("10000.000000", Currency.USDT), new BigDecimal("5000")));

        mvc.perform(post("/merchants/1/withdrawals")
                        .header("Idempotency-Key", "wd-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currency\":\"USDT\",\"amount\":\"10000.000000\"," +
                                "\"destination\":{\"address\":\"TXYZ0000000000000\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_LIMIT_EXCEEDED"));
    }
}
