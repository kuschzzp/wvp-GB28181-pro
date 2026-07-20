package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyAuthenticationService authenticationService;
    private final AnonymousAuthenticationEntryPoint authenticationEntryPoint;

    public ApiKeyAuthenticationFilter(
            ApiKeyAuthenticationService authenticationService,
            AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationService = authenticationService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        if (hasText(request.getHeader(JwtUtils.HEADER))
                && hasText(request.getHeader(JwtUtils.API_KEY_HEADER))) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InvalidAccessTokenException("不能同时提供 access-token 和 api-key"));
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            filterChain.doFilter(request, response);
            return;
        }
        String rawKey = request.getHeader(JwtUtils.API_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            AuthPrincipal principal = authenticationService.authenticate(rawKey, clientAddress(request));
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, principal.authorities());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            InvalidAccessTokenException authenticationException = exception instanceof InvalidAccessTokenException invalid
                    ? invalid
                    : new InvalidAccessTokenException("API Key 校验失败", exception);
            authenticationEntryPoint.commence(request, response, authenticationException);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private InetAddress clientAddress(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr());
        } catch (UnknownHostException exception) {
            throw new InvalidAccessTokenException("客户端地址无效", exception);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
