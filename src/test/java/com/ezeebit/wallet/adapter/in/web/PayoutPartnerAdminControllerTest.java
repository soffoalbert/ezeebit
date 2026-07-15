package com.ezeebit.wallet.adapter.in.web;

import com.ezeebit.wallet.application.port.in.ManagePayoutPartnersUseCase;
import com.ezeebit.wallet.domain.exception.PayoutPartnerNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PayoutPartnerAdminController.class)
class PayoutPartnerAdminControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    ManagePayoutPartnersUseCase managePartners;

    @Test
    void togglingHealthReturns204() throws Exception {
        doNothing().when(managePartners).setHealthy(eq("za-swift-eft"), eq(false));

        mvc.perform(patch("/internal/payout-partners/za-swift-eft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthy\":false}"))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownPartnerReturns404() throws Exception {
        doThrow(new PayoutPartnerNotFoundException("nope"))
                .when(managePartners).setHealthy(eq("nope"), eq(false));

        mvc.perform(patch("/internal/payout-partners/nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"healthy\":false}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PARTNER_NOT_FOUND"));
    }

    @Test
    void missingHealthyFieldIsRejected() throws Exception {
        mvc.perform(patch("/internal/payout-partners/za-swift-eft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
