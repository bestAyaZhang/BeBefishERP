package com.bebefish.erp.file.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FileControllerTest {
    @TempDir static Path uploadDirectory;

    @DynamicPropertySource
    static void configureUploadDirectory(DynamicPropertyRegistry registry) {
        registry.add("ERP_UPLOAD_DIR", () -> uploadDirectory.toString());
    }

    @Autowired MockMvc mvc;
    @Autowired TokenIssuer tokenIssuer;
    @Autowired ObjectMapper objectMapper;
    private String editToken;
    private String viewToken;

    @BeforeEach
    void setUp() {
        editToken = token("product:edit");
        viewToken = token("product:view");
    }

    @Test
    void uploadsImageCreatesAssetAndServesItFromReturnedUrl() throws Exception {
        var image = new MockMultipartFile("file", "T9-glass.png", "image/png", pngBytes());

        var response = mvc.perform(multipart("/api/files/images")
                        .file(image)
                        .header("Authorization", bearer(editToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.contentType").value("image/png"))
                .andExpect(jsonPath("$.data.size").value(pngBytes().length))
                .andReturn().getResponse().getContentAsString();
        var url = objectMapper.readTree(response).path("data").path("url").asText();

        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("image/png"))
                .andExpect(content().bytes(pngBytes()));
    }

    @Test
    void returnsNotFoundWhenUploadedImageDoesNotExist() throws Exception {
        mvc.perform(get("/uploads/missing-image.png"))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsUnsupportedMultipartFile() throws Exception {
        var file = new MockMultipartFile("file", "T9-quote.pdf", "application/pdf", new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/files/images")
                        .file(file)
                        .header("Authorization", bearer(editToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IMAGE_TYPE_NOT_ALLOWED"));
    }

    @Test
    void rejectsUploadForViewOnlyUser() throws Exception {
        var image = new MockMultipartFile("file", "T9-glass.png", "image/png", pngBytes());

        mvc.perform(multipart("/api/files/images")
                        .file(image)
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isForbidden());
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0x00, 0x00};
    }

    private String token(String... permissions) {
        return tokenIssuer.issue(new AuthenticatedUser("13900000006",
                List.of("TESTER"), List.of(permissions)), "test").accessToken();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
