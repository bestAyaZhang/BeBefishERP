package com.bebefish.erp.auth.api;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.application.FeishuLoginService;
import com.bebefish.erp.auth.application.LoginResult;
import com.bebefish.erp.auth.application.OAuthStateService;
import com.bebefish.erp.auth.domain.LoginAuditEvent;
import com.bebefish.erp.auth.domain.LoginAuditRepository;
import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.feishu.FeishuAvailability;
import com.bebefish.erp.feishu.FeishuOAuthClient;
import com.bebefish.erp.feishu.FeishuProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/feishu")
public class FeishuAuthController {
    private static final Logger log = LoggerFactory.getLogger(FeishuAuthController.class);
    static final String STATE_COOKIE = "erp_feishu_state";
    private static final String RESULT_PATH = "/auth/feishu/result";

    private final FeishuAvailability availability;
    private final FeishuOAuthClient oauth;
    private final OAuthStateService states;
    private final FeishuLoginService loginService;
    private final boolean secureStateCookie;
    private final LoginAuditRepository audits;
    private final Clock clock;

    public FeishuAuthController(
            FeishuAvailability availability,
            FeishuOAuthClient oauth,
            OAuthStateService states,
            FeishuLoginService loginService,
            FeishuProperties properties,
            LoginAuditRepository audits,
            Clock clock
    ) {
        this.availability = availability;
        this.oauth = oauth;
        this.states = states;
        this.loginService = loginService;
        this.secureStateCookie = "https".equalsIgnoreCase(
                java.net.URI.create(properties.getRedirectUri()).getScheme()
        );
        this.audits = audits;
        this.clock = clock;
    }

    @GetMapping("/status")
    public ApiResponse<FeishuAvailability> status() {
        return ApiResponse.success(availability);
    }

    @GetMapping("/authorize")
    public void authorize(HttpServletResponse response) throws IOException {
        if (!availability.available()) {
            throw new AuthException("FEISHU_NOT_CONFIGURED", "飞书登录暂不可用");
        }
        String state = states.issue();
        response.addHeader(HttpHeaders.SET_COOKIE, stateCookie(state, Duration.ofMinutes(5)).toString());
        response.sendRedirect(oauth.authorizationUri(state).toString());
    }

    @GetMapping("/callback")
    public void callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @CookieValue(value = STATE_COOKIE, required = false) String cookieState,
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        boolean delegatedToLoginService = false;
        String redirectName;
        String redirectValue;
        try {
            states.consume(state, cookieState);
            if (code == null || code.isBlank()) {
                throw new AuthException("FEISHU_CALLBACK_EXPIRED", "飞书回调缺少授权码");
            }
            delegatedToLoginService = true;
            String ticket = loginService.login(code, request.getRemoteAddr(), request.getHeader("User-Agent"));
            redirectName = "ticket";
            redirectValue = ticket;
        } catch (AuthException exception) {
            if (!delegatedToLoginService) {
                recordAuditSafely(new LoginAuditEvent(
                        null, "feishu", "failure", exception.code(), null,
                        request.getRemoteAddr(), truncate(request.getHeader("User-Agent")), Instant.now(clock)
                ));
            }
            redirectName = "error";
            redirectValue = exception.code();
        } catch (RuntimeException exception) {
            recordAuditSafely(new LoginAuditEvent(
                    null, "feishu", "failure", "LOGIN_FAILED", null,
                    request.getRemoteAddr(), truncate(request.getHeader("User-Agent")), Instant.now(clock)
            ));
            redirectName = "error";
            redirectValue = "LOGIN_FAILED";
        } finally {
            response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO).toString());
        }
        redirect(response, redirectName, redirectValue);
    }

    private void recordAuditSafely(LoginAuditEvent event) {
        try {
            audits.record(event);
        } catch (RuntimeException auditFailure) {
            log.error("Failed to persist Feishu callback audit", auditFailure);
        }
    }

    @PostMapping("/exchange")
    public ApiResponse<LoginResult> exchange(@Valid @RequestBody FeishuExchangeRequest request) {
        return ApiResponse.success(loginService.exchange(request.ticket()));
    }

    private void redirect(HttpServletResponse response, String name, String value) throws IOException {
        response.sendRedirect(RESULT_PATH + "?" + name + "="
                + URLEncoder.encode(value, StandardCharsets.UTF_8));
    }

    private ResponseCookie stateCookie(String value, Duration maxAge) {
        return ResponseCookie.from(STATE_COOKIE, value)
                .httpOnly(true)
                .secure(secureStateCookie)
                .sameSite("Lax")
                .path("/api/auth/feishu")
                .maxAge(maxAge)
                .build();
    }

    private String truncate(String value) {
        return value == null ? null : value.substring(0, Math.min(value.length(), 500));
    }
}
