package com.bebefish.erp.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.bebefish.erp.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(BearerTokenAuthenticationFilterTest.ProtectedProbeController.class)
class BearerTokenAuthenticationFilterTest {
    private static final List<String> ADMIN_PERMISSIONS = List.of(
            "masterdata:view", "masterdata:edit",
            "product:view", "product:edit",
            "inventory:view", "inventory:adjust",
            "sales:view", "sales:create", "sales:confirm", "sales:void", "sales:print",
            "finance:view", "finance:receipt"
    );

    @Autowired
    MockMvc mvc;

    @Autowired
    TokenIssuer tokenIssuer;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void rejectsBusinessApiWithoutToken() throws Exception {
        mvc.perform(get("/api/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("未登录"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void rejectsInvalidBearerTokenWithUniformResponse() throws Exception {
        mvc.perform(get("/api/test/product")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("未登录"));
    }

    @Test
    void leavesPasswordLoginApiPublicAndReturnsAllAdminPermissions() throws Exception {
        var response = mvc.perform(post("/api/auth/login/password")
                        .contentType(APPLICATION_JSON)
                        .content("{\"mobile\":\"13800138000\",\"password\":\"Admin@123456\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var permissions = objectMapper.readTree(response).path("data").path("permissions");
        List<String> actualPermissions = objectMapper.convertValue(
                permissions,
                objectMapper.getTypeFactory().constructCollectionType(List.class, String.class)
        );
        assertThat(actualPermissions).containsAll(ADMIN_PERMISSIONS);
    }

    @Test
    void rejectsAuthenticatedUserWithoutRequiredPermission() throws Exception {
        var token = issueToken(List.of("masterdata:view"));

        mvc.perform(get("/api/test/product")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void allowsAuthenticatedUserWithRequiredPermission() throws Exception {
        var token = issueToken(List.of("product:view"));

        mvc.perform(get("/api/test/product")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("product-visible"));
    }

    private String issueToken(List<String> permissions) {
        var user = new AuthenticatedUser(
                "13900000000", List.of("TESTER"), permissions
        );
        return tokenIssuer.issue(user, "test").accessToken();
    }

    @RestController
    static class ProtectedProbeController {
        @GetMapping("/api/test/product")
        @PreAuthorize("hasAuthority('product:view')")
        ApiResponse<String> viewProduct() {
            return ApiResponse.success("product-visible");
        }
    }
}
