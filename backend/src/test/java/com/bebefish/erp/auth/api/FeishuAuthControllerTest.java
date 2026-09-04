package com.bebefish.erp.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "erp.feishu.enabled=true",
        "erp.feishu.app-id=mock-app",
        "erp.feishu.app-secret=mock-secret",
        "erp.feishu.redirect-uri=http://127.0.0.1:5173/api/auth/feishu/callback",
        "erp.feishu.allowed-tenant-key=tenant-a",
        "erp.feishu.mock-enabled=true"
})
@Transactional
class FeishuAuthControllerTest {
    @Autowired
    private MockMvc mvc;

    @Test
    void authorizeCallbackAndExchangeCompleteWithoutPuttingSessionInUrl() throws Exception {
        var authorization = mvc.perform(get("/api/auth/feishu/authorize"))
                .andExpect(status().isFound())
                .andExpect(cookie().httpOnly("erp_feishu_state", true))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Lax")))
                .andExpect(header().string("Location", containsString("state=")))
                .andReturn();
        Cookie stateCookie = authorization.getResponse().getCookie("erp_feishu_state");
        String state = query(authorization.getResponse().getHeader("Location"), "state");

        var callback = mvc.perform(get("/api/auth/feishu/callback")
                        .param("code", "mock-no-mobile")
                        .param("state", state)
                        .cookie(stateCookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("/auth/feishu/result?ticket=")))
                .andExpect(header().string("Location", org.hamcrest.Matchers.not(containsString("accessToken"))))
                .andReturn();
        String ticket = query(callback.getResponse().getHeader("Location"), "ticket");

        mvc.perform(post("/api/auth/feishu/exchange")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"" + ticket + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginMethod").value("feishu"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.roles[0]").value("BASIC_EMPLOYEE"));
    }

    @Test
    void stateIsRequiredAndCanOnlyBeConsumedOnce() throws Exception {
        var authorization = mvc.perform(get("/api/auth/feishu/authorize")).andReturn();
        Cookie cookie = authorization.getResponse().getCookie("erp_feishu_state");
        String state = query(authorization.getResponse().getHeader("Location"), "state");

        mvc.perform(get("/api/auth/feishu/callback")
                        .param("code", "mock-no-mobile")
                        .param("state", "wrong")
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("error=FEISHU_STATE_INVALID")));

        mvc.perform(get("/api/auth/feishu/callback")
                        .param("code", "mock-no-mobile")
                        .param("state", state)
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("ticket=")));

        mvc.perform(get("/api/auth/feishu/callback")
                        .param("code", "mock-no-mobile")
                        .param("state", state)
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("error=FEISHU_STATE_INVALID")));
    }

    @Test
    void removedSmsRoutesReturnNotFound() throws Exception {
        mvc.perform(post("/api/auth/sms-code").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/auth/login/sms").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());
    }

    private String query(String location, String name) {
        return java.util.Arrays.stream(URI.create(location).getQuery().split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(pair -> pair[0].equals(name))
                .map(pair -> java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }
}
