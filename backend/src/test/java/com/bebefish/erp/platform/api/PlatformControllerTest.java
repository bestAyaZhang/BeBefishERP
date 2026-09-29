package com.bebefish.erp.platform.api;

import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.support.TestAuthTokens;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.hamcrest.Matchers.hasItem;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional
class PlatformControllerTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TokenIssuer tokens;
    @Autowired ObjectMapper mapper;
    String admin() { return TestAuthTokens.issue(jdbc,tokens,"13900007700","platform:view","platform:create","platform:edit"); }
    JsonNode create(String path,String json,String token) throws Exception {
        var response=mvc.perform(post(path).header("Authorization","Bearer "+token).contentType(APPLICATION_JSON).content(json))
            .andExpect(status().isOk()).andReturn().getResponse();
        return mapper.readTree(response.getContentAsByteArray()).get("data");
    }
    @Test void catalogCrudEnforcesVersionUniquenessAndParentStatus() throws Exception {
        String t=admin();
        var p=create("/api/platforms","{\"code\":\" test_p \" ,\"name\":\" 测试平台 \"}",t);
        assertThat(p.get("code").asText()).isEqualTo("TEST_P");
        long id=p.get("id").asLong();
        var s=create("/api/platform-shops","{\"platformId\":"+id+",\"code\":\"S1\",\"name\":\"一号店\",\"channelType\":\"ecommerce\",\"ownerName\":\"运营\",\"optionLabel\":\"一号店选项\"}",t);
        mvc.perform(post("/api/platforms").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"code\":\"test_p\",\"name\":\"另一平台\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_CODE_DUPLICATE"));
        mvc.perform(post("/api/platforms/"+id+"/status").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON).content("{\"status\":\"disabled\",\"version\":0}"))
            .andExpect(status().isOk());
        mvc.perform(put("/api/platforms/"+id).header("Authorization","Bearer "+t).contentType(APPLICATION_JSON).content("{\"name\":\"覆盖\",\"version\":0}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_VERSION_CONFLICT"));
        mvc.perform(get("/api/platform-shops/"+s.get("id").asLong()).header("Authorization","Bearer "+t))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("enabled")).andExpect(jsonPath("$.data.platformStatus").value("disabled"));
        mvc.perform(post("/api/platform-shops").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"platformId\":"+id+",\"code\":\"S2\",\"name\":\"二号店\",\"channelType\":\"ecommerce\",\"ownerName\":\"运营\",\"optionLabel\":\"二号店选项\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PLATFORM_DISABLED"));
    }
    @Test void permissionsAreIndependentOfShipping() throws Exception {
        mvc.perform(get("/api/platforms")).andExpect(status().isUnauthorized());
        String t=TestAuthTokens.issue(jdbc,tokens,"13900007701","shipping:view");
        mvc.perform(get("/api/platforms").header("Authorization","Bearer "+t)).andExpect(status().isForbidden());
        t=TestAuthTokens.issue(jdbc,tokens,"13900007702","platform:view");
        mvc.perform(get("/api/platforms").header("Authorization","Bearer "+t)).andExpect(status().isOk());
        mvc.perform(post("/api/platforms").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON).content("{\"code\":\"P\",\"name\":\"平台\"}"))
            .andExpect(status().isForbidden());
    }
    @Test void rejectsInvalidFiltersAndAllowsSameNameAcrossPlatforms() throws Exception {
        String t=admin();
        var a=create("/api/platforms","{\"code\":\"A\",\"name\":\"甲\"}",t);
        var b=create("/api/platforms","{\"code\":\"B\",\"name\":\"乙\"}",t);
        create("/api/platform-shops","{\"platformId\":"+a.get("id")+",\"code\":\"SA\",\"name\":\"同名店\",\"channelType\":\"ecommerce\",\"ownerName\":\"运营\",\"optionLabel\":\"甲店选项\"}",t);
        create("/api/platform-shops","{\"platformId\":"+b.get("id")+",\"code\":\"SB\",\"name\":\"同名店\",\"channelType\":\"ecommerce\",\"ownerName\":\"运营\",\"optionLabel\":\"乙店选项\"}",t);
        mvc.perform(get("/api/platforms?size=101").header("Authorization","Bearer "+t)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/platform-shops?keyword=%25").header("Authorization","Bearer "+t)).andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/platform-shops?status=unknown").header("Authorization","Bearer "+t)).andExpect(status().isBadRequest());
    }
    @Test void generatesInternalCodesAndPersistsFeishuShopFields() throws Exception {
        String t=admin();
        var p=create("/api/platforms","{\"name\":\"拼多多测试\"}",t);
        assertThat(p.get("code").asText()).matches("P_[A-Z0-9]{32}");
        long platformId=p.get("id").asLong();
        var shop=create("/api/platform-shops","{\"platformId\":"+platformId+",\"name\":\"笨笨的生活商铺\",\"channelType\":\"ecommerce\",\"ownerName\":\"韩晓兵\",\"optionLabel\":\"客户端伪造标签\"}",t);
        assertThat(shop.get("code").asText()).matches("S_[A-Z0-9]{32}");
        assertThat(shop.get("channelType").asText()).isEqualTo("ecommerce");
        assertThat(shop.get("ownerName").asText()).isEqualTo("韩晓兵");
        assertThat(shop.get("optionLabel").asText()).isEqualTo("电商_拼多多测试_笨笨的生活商铺");
        long shopId=shop.get("id").asLong();
        mvc.perform(put("/api/platform-shops/"+shopId).header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"name\":\"笨笨的生活新商铺\",\"version\":0,\"channelType\":\"ecommerce\",\"ownerName\":\"张振亚\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.ownerName").value("张振亚"))
            .andExpect(jsonPath("$.data.optionLabel").value("电商_拼多多测试_笨笨的生活新商铺"));
        String shipping=TestAuthTokens.issue(jdbc,tokens,"13900007703","shipping:view");
        mvc.perform(get("/api/shipments/form-options").header("Authorization","Bearer "+shipping))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shops[?(@.id == "+shopId+")].optionLabel").value(hasItem("电商_拼多多测试_笨笨的生活新商铺")))
            .andExpect(jsonPath("$.data.shops[0].ownerName").doesNotExist());
        mvc.perform(get("/api/shipments/filter-options").header("Authorization","Bearer "+shipping))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.shops[?(@.id == "+shopId+")].optionLabel").value(hasItem("电商_拼多多测试_笨笨的生活新商铺")));
        mvc.perform(post("/api/platform-shops").header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"platformId\":"+platformId+",\"name\":\"缺少配置\"}"))
            .andExpect(status().isBadRequest());
    }
    @Test void keepsPrivateExceptionAndRegeneratesOrdinaryLabelsAfterPlatformRename() throws Exception {
        String t=admin();
        var p=create("/api/platforms","{\"name\":\"测试抖音代发\"}",t);
        long platformId=p.get("id").asLong();
        var exception=create("/api/platform-shops","{\"platformId\":"+platformId+",\"name\":\"代发1\",\"channelType\":\"private\",\"ownerName\":\"安正鹏\",\"optionLabelOverride\":\"私域_代发_方钉\"}",t);
        var ordinary=create("/api/platform-shops","{\"platformId\":"+platformId+",\"name\":\"代发4\",\"channelType\":\"private\",\"ownerName\":\"安正鹏\"}",t);
        assertThat(exception.get("optionLabel").asText()).isEqualTo("私域_代发_方钉");
        assertThat(ordinary.get("optionLabel").asText()).isEqualTo("私域_代发_代发4");
        mvc.perform(put("/api/platform-shops/"+exception.get("id").asLong()).header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"name\":\"代发一号\",\"version\":0,\"channelType\":\"private\",\"ownerName\":\"安正鹏\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.optionLabel").value("私域_代发_方钉"));
        mvc.perform(put("/api/platforms/"+platformId).header("Authorization","Bearer "+t).contentType(APPLICATION_JSON)
            .content("{\"name\":\"新平台\",\"version\":0}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/platform-shops/"+ordinary.get("id").asLong()).header("Authorization","Bearer "+t))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.optionLabel").value("私域_新平台_代发4"));
        mvc.perform(get("/api/platform-shops/"+exception.get("id").asLong()).header("Authorization","Bearer "+t))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.optionLabel").value("私域_代发_方钉"));
    }
}
