package com.genersoft.iot.vmp.conf.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.security.RequestIdResolver;
import com.genersoft.iot.vmp.auth.security.SecurityError;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LogoutHandler implements LogoutSuccessHandler {

    private final JwtUtils jwtUtils;
    private final TokenRevocationService tokenRevocationService;
    private final TokenVersionService tokenVersionService;
    private final ObjectMapper objectMapper;

    public LogoutHandler(
            JwtUtils jwtUtils,
            TokenRevocationService tokenRevocationService,
            TokenVersionService tokenVersionService,
            ObjectMapper objectMapper) {
        this.jwtUtils = jwtUtils;
        this.tokenRevocationService = tokenRevocationService;
        this.tokenVersionService = tokenVersionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onLogoutSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {
        String rawToken = request.getHeader(JwtUtils.HEADER);
        if (rawToken == null || rawToken.isBlank()) {
            writeUnauthorized(request, response);
            return;
        }

        final VerifiedAccessToken token;
        try {
            token = jwtUtils.verifyAccessToken(rawToken);
        } catch (RuntimeException exception) {
            writeUnauthorized(request, response);
            return;
        }

        try {
            if (!tokenVersionService.matches(token.userId(), token.tokenVersion())) {
                writeUnauthorized(request, response);
                return;
            }
        } catch (RuntimeException versionCheckFailure) {
            log.error("退出时无法校验 tokenVersion, userId={}",
                    token.userId(), versionCheckFailure);
            write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    WVPResult.fail(503, "退出未完成，请稍后重试"));
            return;
        }

        try {
            tokenRevocationService.revoke(token);
        } catch (RuntimeException redisFailure) {
            log.error("退出时写入 jti 撤销记录失败，改为递增用户 tokenVersion, userId={}",
                    token.userId(), redisFailure);
            try {
                tokenVersionService.bumpUser(token.userId(), "LOGOUT_REDIS_FALLBACK");
            } catch (RuntimeException databaseFailure) {
                log.error("退出兜底失败，旧 token 仍可能有效, userId={}", token.userId(), databaseFailure);
                write(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        WVPResult.fail(503, "退出未完成，请稍后重试"));
                return;
            }
        }

        log.info("用户退出成功, userId={}, jti={}", token.userId(), token.jti());
        write(response, HttpServletResponse.SC_OK, WVPResult.success());
    }

    private void writeUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        write(response, HttpServletResponse.SC_UNAUTHORIZED, new SecurityError(
                ErrorCode.ERROR401.getCode(),
                ErrorCode.ERROR401.getMsg(),
                RequestIdResolver.resolve(request)));
    }

    private void write(HttpServletResponse response, int status, Object body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
