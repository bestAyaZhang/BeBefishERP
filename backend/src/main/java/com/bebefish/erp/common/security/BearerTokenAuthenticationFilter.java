package com.bebefish.erp.common.security;

import com.bebefish.erp.auth.application.AuthException;
import com.bebefish.erp.auth.domain.TokenIssuer;
import com.bebefish.erp.common.api.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

public final class BearerTokenAuthenticationFilter extends OncePerRequestFilter {
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenIssuer tokenIssuer;
    private final ObjectMapper objectMapper;

    public BearerTokenAuthenticationFilter(TokenIssuer tokenIssuer, ObjectMapper objectMapper) {
        this.tokenIssuer = tokenIssuer;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (!hasBearerToken(authorization)) {
            filterChain.doFilter(request, response);
            return;
        }

        var token = authorization.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            writeUnauthorized(response);
            return;
        }

        try {
            var login = tokenIssuer.resolve(token);
            var principal = new ErpPrincipal(
                    login.employeeId(), login.mobile(), login.displayName(), login.roles(), login.permissions()
            );
            var authorities = Stream.concat(
                            login.permissions().stream().map(SimpleGrantedAuthority::new),
                            login.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    )
                    .distinct()
                    .toList();
            var authentication = new UsernamePasswordAuthenticationToken(principal, null, authorities);
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            filterChain.doFilter(request, response);
        } catch (AuthException exception) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(response);
        }
    }

    private boolean hasBearerToken(String authorization) {
        return authorization != null
                && authorization.length() >= BEARER_PREFIX.length()
                && authorization.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length());
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure("UNAUTHORIZED", "未登录", null)
        );
    }
}
