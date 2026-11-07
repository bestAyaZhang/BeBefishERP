package com.bebefish.erp.inventory.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.common.api.GlobalExceptionHandler;
import com.bebefish.erp.inventory.application.ReplenishmentCalculator.Config;
import com.bebefish.erp.inventory.application.ReplenishmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReplenishmentControllerTest {
    private final ReplenishmentService service = mock(ReplenishmentService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new ReplenishmentController(service))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @ParameterizedTest
    @CsvSource({"-0.2,120", "5,120.9", "5,9223372036854775808"})
    void rejectsNonIntegralAndOverflowingValuesBeforeSaving(String bufferDays, String manualPoint) throws Exception {
        mvc.perform(put("/api/inventory/replenishment/23")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"mode":"manual","bufferDays":%s,"leadDays":8,"manualSafety":48,"manualPoint":%s}
                                """.formatted(bufferDays, manualPoint)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }

    @Test
    void passesValidWholeNumbersAndOptionalFallbackToTheService() throws Exception {
        mvc.perform(put("/api/inventory/replenishment/23")
                        .param("warehouseId", "3")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"mode":"auto","bufferDays":5,"leadDays":8,"manualSafety":null,"manualPoint":null}
                                """))
                .andExpect(status().isOk());

        verify(service).save(23L, 3L, new Config("auto", 5, 8, null, null));
    }

    @Test
    void rejectsFractionalBatchIdsBeforeSaving() throws Exception {
        mvc.perform(put("/api/inventory/replenishment/batch")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"targets":[{"skuId":23,"warehouseId":3.5}],
                                 "config":{"mode":"auto","bufferDays":5,"leadDays":8}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(service);
    }
}
