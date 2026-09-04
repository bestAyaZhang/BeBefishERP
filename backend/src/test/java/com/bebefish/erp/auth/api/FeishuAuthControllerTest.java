package com.bebefish.erp.auth.api;

import static org.hamcrest.Matchers.containsString;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import com.bebefish.erp.auth.application.FeishuLoginService;
import com.bebefish.erp.auth.application.OAuthStateService;
import com.bebefish.erp.auth.domain.LoginAuditEvent;
import com.bebefish.erp.auth.domain.LoginAuditRepository;
import com.bebefish.erp.feishu.FeishuAvailability;
import com.bebefish.erp.feishu.FeishuOAuthClient;
import com.bebefish.erp.feishu.FeishuProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.mock.web.MockHttpServletResponse;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
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

    @Autowired
    private JdbcTemplate jdbc;

    @SpyBean
    private FeishuOAuthClient oauthClient;

    @SpyBean
    private LoginAuditRepository auditRepository;

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
        int failuresBefore = jdbc.queryForObject(
                "select count(*) from sys_login_audit where error_code = 'FEISHU_STATE_INVALID'",
                Integer.class
        );
        var authorization = mvc.perform(get("/api/auth/feishu/authorize")).andReturn();
        Cookie cookie = authorization.getResponse().getCookie("erp_feishu_state");
        String state = query(authorization.getResponse().getHeader("Location"), "state");

        mvc.perform(get("/api/auth/feishu/callback")
                        .param("code", "mock-no-mobile")
                        .param("state", "wrong")
                        .cookie(cookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("error=FEISHU_STATE_INVALID")));
        assertThat(jdbc.queryForObject(
                "select count(*) from sys_login_audit where error_code = 'FEISHU_STATE_INVALID'",
                Integer.class
        )).isEqualTo(failuresBefore + 1);

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

    @Test
    void unexpectedCallbackFailureRedirectsAuditsAndClearsStateCookie() throws Exception {
        int failuresBefore = jdbc.queryForObject(
                "select count(*) from sys_login_audit where error_code = 'LOGIN_FAILED'",
                Integer.class
        );
        var authorization = mvc.perform(get("/api/auth/feishu/authorize")).andReturn();
        Cookie stateCookie = authorization.getResponse().getCookie("erp_feishu_state");
        String state = query(authorization.getResponse().getHeader("Location"), "state");
        doThrow(new IllegalStateException("unexpected failure"))
                .when(oauthClient).exchangeCode("mock-unexpected");

        mvc.perform(get("/api/auth/feishu/callback")
                        .param("code", "mock-unexpected")
                        .param("state", state)
                        .cookie(stateCookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("error=LOGIN_FAILED")))
                .andExpect(cookie().maxAge("erp_feishu_state", 0));

        assertThat(jdbc.queryForObject(
                "select count(*) from sys_login_audit where error_code = 'LOGIN_FAILED'",
                Integer.class
        )).isEqualTo(failuresBefore + 1);
    }

    @Test
    void auditStorageFailureCannotBreakSafeCallbackRedirect() throws Exception {
        var authorization = mvc.perform(get("/api/auth/feishu/authorize")).andReturn();
        Cookie stateCookie = authorization.getResponse().getCookie("erp_feishu_state");
        String state = query(authorization.getResponse().getHeader("Location"), "state");
        doThrow(new IllegalStateException("audit storage unavailable"))
                .when(auditRepository).record(any(LoginAuditEvent.class));

        mvc.perform(get("/api/auth/feishu/callback")
                        .param("state", state)
                        .cookie(stateCookie))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", containsString("error=FEISHU_CALLBACK_EXPIRED")))
                .andExpect(cookie().maxAge("erp_feishu_state", 0));
    }

    @Test
    void httpsRedirectUsesSecureStateCookie() throws Exception {
        var properties = new FeishuProperties();
        properties.setRedirectUri("https://erp.example.com/api/auth/feishu/callback");
        var oauth = mock(FeishuOAuthClient.class);
        var states = mock(OAuthStateService.class);
        when(states.issue()).thenReturn("secure-state");
        when(oauth.authorizationUri("secure-state")).thenReturn(URI.create("https://accounts.feishu.cn/auth"));
        var controller = new FeishuAuthController(
                FeishuAvailability.ready(), oauth, states, mock(FeishuLoginService.class), properties,
                mock(com.bebefish.erp.auth.domain.LoginAuditRepository.class), java.time.Clock.systemUTC()
        );
        var response = new MockHttpServletResponse();

        controller.authorize(response);

        assertThat(response.getHeader("Set-Cookie")).contains("Secure");
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
