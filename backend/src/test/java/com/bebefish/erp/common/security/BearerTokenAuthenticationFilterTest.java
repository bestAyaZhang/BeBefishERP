package com.bebefish.erp.common.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.application.LoginResult;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.common.config.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = BearerTokenAuthenticationFilterTest.ProtectedProbeController.class)
@Import({SecurityConfig.class, BearerTokenAuthenticationFilterTest.ProtectedProbeController.class})
class BearerTokenAuthenticationFilterTest {
    @Autowired
    MockMvc mvc;

    @MockBean
    TokenIssuer tokenIssuer;

    @Test
    void rejectsBusinessApiWithoutToken() throws Exception {
        mvc.perform(get("/api/test/product"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("未登录"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsInvalidBearerTokenWithUniformResponse() throws Exception {
        when(tokenIssuer.resolve("invalid-token"))
                .thenThrow(new AuthException("UNAUTHORIZED", "未登录"));

        mvc.perform(get("/api/test/product")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void leavesPasswordLoginApiPublic() throws Exception {
        mvc.perform(post("/api/auth/login/password"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("public-login"));
    }

    @Test
    void leavesReadOnlyImageContentPublicForBrowserImageTags() throws Exception {
        mvc.perform(get("/api/files/content/image.jpg"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("public-image"));
    }

    @Test
    void rejectsUnknownFeishuAuthPathWithoutToken() throws Exception {
        mvc.perform(get("/api/auth/feishu/unexpected"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAuthenticatedUserWithoutRequiredPermission() throws Exception {
        resolveToken("missing-product", List.of("masterdata:view"));

        mvc.perform(get("/api/test/product")
                        .header("Authorization", "Bearer missing-product"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsAuthenticatedUserWithRequiredPermission() throws Exception {
        resolveToken("product-viewer", List.of("product:view"));

        mvc.perform(get("/api/test/product")
                        .header("Authorization", "Bearer product-viewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("product-visible"));
    }

    private void resolveToken(String accessToken, List<String> permissions) {
        when(tokenIssuer.resolve(accessToken)).thenReturn(new LoginResult(
                accessToken,
                "13900000000",
                List.of("TESTER"),
                permissions,
                "test"
        ));
    }

    @RestController
    static class ProtectedProbeController {
        @GetMapping("/api/test/product")
        @PreAuthorize("hasAuthority('product:view')")
        ApiResponse<String> viewProduct() {
            return ApiResponse.success("product-visible");
        }

        @PostMapping("/api/auth/login/password")
        ApiResponse<String> passwordLogin() {
            return ApiResponse.success("public-login");
        }

        @GetMapping("/api/files/content/image.jpg")
        ApiResponse<String> imageContent() {
            return ApiResponse.success("public-image");
        }
    }
}
