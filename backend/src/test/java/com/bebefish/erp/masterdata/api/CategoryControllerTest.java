package com.bebefish.erp.masterdata.api;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.auth.domain.AuthenticatedUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CategoryControllerTest {
    @Autowired
    MockMvc mvc;

    @Autowired
    TokenIssuer tokenIssuer;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    private String editToken;
    private String viewToken;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from product_category where category_code like 'T3-%'");
        jdbc.update("delete from product_category where category_name = '自动编号分类-T3'");
        editToken = issueToken("category:view", "category:create", "category:edit");
        viewToken = issueToken("category:view");
    }

    @Test
    void createsLevelOneCategoryAndReadsDetail() throws Exception {
        var response = createCategory("T3-GLASS", "玻璃杯", 10);
        var id = objectMapper.readTree(response).path("data").path("id").asLong();

        mvc.perform(get("/api/categories/{id}", id)
                        .header("Authorization", bearer(viewToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryCode").value("T3-GLASS"))
                .andExpect(jsonPath("$.data.categoryName").value("玻璃杯"))
                .andExpect(jsonPath("$.data.parentId").doesNotExist())
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.status").value("enabled"));
    }

    @Test
    void createsCategoryWithGeneratedCodeWhenCodeIsOmitted() throws Exception {
        mvc.perform(post("/api/categories")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryName":"自动编号分类-T3","sortOrder":10}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryCode").value(org.hamcrest.Matchers.matchesRegex("CAT-[0-9]{8}-[A-Z0-9]{6}")));
    }

    @Test
    void listsCategoriesByKeywordAndStatusWithOneBasedPaging() throws Exception {
        createCategory("T3-BEER", "啤酒杯", 20);
        createCategory("T3-WINE", "红酒杯", 30);

        mvc.perform(get("/api/categories")
                        .header("Authorization", bearer(viewToken))
                        .param("page", "1")
                        .param("size", "10")
                        .param("keyword", "T3-BEER")
                        .param("status", "enabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].categoryCode").value("T3-BEER"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(10))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    void updatesAndDisablesCategory() throws Exception {
        var response = createCategory("T3-ASH", "烟灰缸", 40);
        var id = objectMapper.readTree(response).path("data").path("id").asLong();

        mvc.perform(put("/api/categories/{id}", id)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryCode":"T3-ASH","categoryName":"玻璃烟灰缸",\
                                "sortOrder":50,"remark":"更新"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("玻璃烟灰缸"))
                .andExpect(jsonPath("$.data.sortOrder").value(50));

        mvc.perform(post("/api/categories/{id}/status", id)
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("{\"status\":\"disabled\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("disabled"));
    }

    @Test
    void rejectsDuplicateCategoryNameWithStableCode() throws Exception {
        createCategory("T3-CUP-A", "测试杯", 10);

        mvc.perform(post("/api/categories")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryCode":"T3-CUP-B","categoryName":"测试杯","sortOrder":20}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_CATEGORY_NAME"));
    }

    @Test
    void forbidsViewOnlyUserFromCreatingCategory() throws Exception {
        mvc.perform(post("/api/categories")
                        .header("Authorization", bearer(viewToken))
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {"categoryCode":"T3-NOPE","categoryName":"无权限分类","sortOrder":10}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    private String createCategory(String code, String name, int sortOrder) throws Exception {
        return mvc.perform(post("/api/categories")
                        .header("Authorization", bearer(editToken))
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryInput(code, name, sortOrder))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryCode").value(code))
                .andExpect(jsonPath("$.data.categoryName").value(name))
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private String issueToken(String... permissions) {
        return com.bebefish.erp.support.TestAuthTokens.issue(
                jdbc, tokenIssuer, "13900000001", permissions);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record CategoryInput(String categoryCode, String categoryName, int sortOrder) {
    }
}
