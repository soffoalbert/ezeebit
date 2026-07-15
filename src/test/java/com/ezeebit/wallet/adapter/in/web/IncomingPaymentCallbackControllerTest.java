package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.application.port.in.NotifyIncomingPaymentUseCase;
import com.ezeebit.wallet.domain.exception.IncomingPaymentConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncomingPaymentCallbackController.class)
class IncomingPaymentCallbackControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    NotifyIncomingPaymentUseCase notifyIncomingPayment;

    private static final String BODY = "{\"merchantId\":1,\"txHash\":\"0xabc\",\"outputIndex\":0,"
            + "\"currency\":\"USDT\",\"amount\":\"100.000000\",\"confirmations\":1}";

    @Test
    void validNotificationReturns204() throws Exception {
        doNothing().when(notifyIncomingPayment).notify(org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/internal/incoming-payments")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNoContent());
    }

    @Test
    void contradictoryReplayReturns409() throws Exception {
        doThrow(new IncomingPaymentConflictException("0xabc", 0))
                .when(notifyIncomingPayment).notify(org.mockito.ArgumentMatchers.any());

        mvc.perform(post("/internal/incoming-payments")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INCOMING_PAYMENT_CONFLICT"));
    }

    @Test
    void missingFieldIsRejected() throws Exception {
        mvc.perform(post("/internal/incoming-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"merchantId\":1}"))
                .andExpect(status().isBadRequest());
    }
}
