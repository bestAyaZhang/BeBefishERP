package com.bebefish.erp.auth.api;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.application.FeishuLoginService;
import com.bebefish.erp.auth.application.LoginResult;
import com.bebefish.erp.auth.application.OAuthStateService;
import com.bebefish.erp.common.api.ApiResponse;
import com.bebefish.erp.feishu.FeishuAvailability;
import com.bebefish.erp.feishu.FeishuOAuthClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    static final String STATE_COOKIE = "erp_feishu_state";
    private static final String RESULT_PATH = "/auth/feishu/result";

    private final FeishuAvailability availability;
    private final FeishuOAuthClient oauth;
    private final OAuthStateService states;
    private final FeishuLoginService loginService;

    public FeishuAuthController(
            FeishuAvailability availability,
            FeishuOAuthClient oauth,
            OAuthStateService states,
            FeishuLoginService loginService
    ) {
        this.availability = availability;
        this.oauth = oauth;
        this.states = states;
        this.loginService = loginService;
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
        try {
            states.consume(state, cookieState);
            if (code == null || code.isBlank()) {
                throw new AuthException("FEISHU_CALLBACK_EXPIRED", "飞书回调缺少授权码");
            }
            String ticket = loginService.login(code, request.getRemoteAddr(), request.getHeader("User-Agent"));
            redirect(response, "ticket", ticket);
        } catch (AuthException exception) {
            redirect(response, "error", exception.code());
        } finally {
            response.addHeader(HttpHeaders.SET_COOKIE, stateCookie("", Duration.ZERO).toString());
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
                .secure(false)
                .sameSite("Lax")
                .path("/api/auth/feishu")
                .maxAge(maxAge)
                .build();
    }
}
