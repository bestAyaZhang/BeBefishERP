package com.bebefish.erp.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ExceptionProbeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, GlobalExceptionHandlerTest.ExceptionProbeController.class})
class GlobalExceptionHandlerTest {
    @Autowired
    MockMvc mvc;

    @Test
    void rendersStableBusinessErrorCode() throws Exception {
        mvc.perform(get("/test/business-error"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ITEM_NO"))
                .andExpect(jsonPath("$.message").value("货号已存在"));
    }

    @RestController
    static class ExceptionProbeController {
        @GetMapping("/test/business-error")
        void businessError() {
            throw new BusinessException("DUPLICATE_ITEM_NO", HttpStatus.CONFLICT, "货号已存在");
        }
    }
}
