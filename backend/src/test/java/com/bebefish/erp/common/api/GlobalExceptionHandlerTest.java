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
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import com.bebefish.erp.file.domain.FileStorageException;

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

    @Test
    void rendersStorageFailureWithoutHidingTheUploadReason() throws Exception {
        mvc.perform(get("/test/storage-error"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FILE_STORAGE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("七牛云图片上传失败：HTTP 401"));
    }

    @Test
    void rendersUploadSizeFailureAsAClientError() throws Exception {
        mvc.perform(get("/test/upload-size-error"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("IMAGE_TOO_LARGE"))
                .andExpect(jsonPath("$.message").value("图片超过 10 MB"));
    }

    @RestController
    static class ExceptionProbeController {
        @GetMapping("/test/business-error")
        void businessError() {
            throw new BusinessException("DUPLICATE_ITEM_NO", HttpStatus.CONFLICT, "货号已存在");
        }

        @GetMapping("/test/storage-error")
        void storageError() {
            throw new FileStorageException("七牛云图片上传失败：HTTP 401");
        }

        @GetMapping("/test/upload-size-error")
        void uploadSizeError() {
            throw new MaxUploadSizeExceededException(10L * 1024 * 1024);
        }
    }
}
