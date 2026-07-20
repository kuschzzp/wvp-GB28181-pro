package com.genersoft.iot.vmp.media.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

public class MediaHookAuthenticationFilter extends OncePerRequestFilter {

    public static final String MEDIA_SERVER_ID_PARAM = "mediaServerId";
    public static final String HOOK_SECRET_PARAM = "hookSecret";
    public static final String HOOK_TIMESTAMP_PARAM = "hookTimestamp";
    public static final String HOOK_NONCE_PARAM = "hookNonce";
    public static final String PRINCIPAL_ATTRIBUTE =
            MediaNodePrincipal.class.getName();

    private static final String ZLM_PATH = "/index/hook/zlm/";
    private static final String ABL_PATH = "/index/hook/abl/";
    private static final int MAX_HOOK_BODY_BYTES = 1024 * 1024;

    private final MediaHookAuthenticationService authenticationService;
    private final MediaHookReplayGuard replayGuard;
    private final ObjectMapper objectMapper;
    private final AnonymousAuthenticationEntryPoint authenticationEntryPoint;

    public MediaHookAuthenticationFilter(
            MediaHookAuthenticationService authenticationService,
            MediaHookReplayGuard replayGuard,
            ObjectMapper objectMapper,
            AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
        this.authenticationService = Objects.requireNonNull(
                authenticationService, "authenticationService must not be null");
        this.replayGuard = Objects.requireNonNull(
                replayGuard, "replayGuard must not be null");
        this.objectMapper = Objects.requireNonNull(
                objectMapper, "objectMapper must not be null");
        this.authenticationEntryPoint = Objects.requireNonNull(
                authenticationEntryPoint, "authenticationEntryPoint must not be null");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            if (!"POST".equalsIgnoreCase(request.getMethod())
                    || hasText(request.getHeader(JwtUtils.HEADER))
                    || hasText(request.getHeader(JwtUtils.API_KEY_HEADER))) {
                throw invalid("媒体 Hook 不接受当前请求方式或用户凭证");
            }

            String path = pathWithinApplication(request);
            MediaNodeType nodeType = nodeType(path);
            byte[] body = readBody(request);
            String queryMediaServerId = request.getParameter(MEDIA_SERVER_ID_PARAM);
            String bodyMediaServerId = bodyMediaServerId(body);
            if (!hasText(queryMediaServerId)
                    || !queryMediaServerId.trim().equals(bodyMediaServerId)) {
                throw invalid("媒体节点与 Hook 请求体不一致");
            }

            MediaNodePrincipal principal = authenticationService.authenticate(
                    nodeType,
                    queryMediaServerId,
                    request.getParameter(HOOK_SECRET_PARAM),
                    clientAddress(request));
            if (!replayGuard.accept(
                    principal,
                    path,
                    body,
                    request.getParameter(HOOK_TIMESTAMP_PARAM),
                    request.getParameter(HOOK_NONCE_PARAM))) {
                throw invalid("媒体 Hook 请求已过期或重复");
            }

            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, List.of());
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);

            CachedBodyRequest wrapped = new CachedBodyRequest(request, body);
            wrapped.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            filterChain.doFilter(wrapped, response);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            InvalidAccessTokenException authenticationException =
                    exception instanceof InvalidAccessTokenException invalid
                            ? invalid
                            : new InvalidAccessTokenException(
                                    "媒体节点认证失败", exception);
            authenticationEntryPoint.commence(
                    request, response, authenticationException);
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        return !path.startsWith("/index/hook/");
    }

    private byte[] readBody(HttpServletRequest request) {
        if (request.getContentLengthLong() > MAX_HOOK_BODY_BYTES) {
            throw invalid("媒体 Hook 请求体过大");
        }
        try {
            byte[] body = request.getInputStream()
                    .readNBytes(MAX_HOOK_BODY_BYTES + 1);
            if (body.length == 0 || body.length > MAX_HOOK_BODY_BYTES) {
                throw invalid("媒体 Hook 请求体无效");
            }
            return body;
        } catch (IOException exception) {
            throw invalid("媒体 Hook 请求体读取失败", exception);
        }
    }

    private String bodyMediaServerId(byte[] body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode node = root == null ? null : root.get(MEDIA_SERVER_ID_PARAM);
            String value = node == null || !node.isTextual()
                    ? null
                    : node.textValue();
            if (!hasText(value)) {
                throw invalid("媒体 Hook 请求体缺少节点 ID");
            }
            return value.trim();
        } catch (IOException exception) {
            throw invalid("媒体 Hook 请求体不是有效 JSON", exception);
        }
    }

    private MediaNodeType nodeType(String path) {
        if (path.startsWith(ZLM_PATH)) {
            return MediaNodeType.ZLM;
        }
        if (path.startsWith(ABL_PATH)) {
            return MediaNodeType.ABL;
        }
        throw invalid("未知媒体 Hook 路径");
    }

    private InetAddress clientAddress(HttpServletRequest request) {
        try {
            return InetAddress.getByName(request.getRemoteAddr());
        } catch (UnknownHostException exception) {
            throw invalid("媒体节点来源地址无效", exception);
        }
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (hasText(contextPath) && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private InvalidAccessTokenException invalid(String message) {
        return new InvalidAccessTokenException(message);
    }

    private InvalidAccessTokenException invalid(
            String message,
            Throwable cause) {
        return new InvalidAccessTokenException(message, cause);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class CachedBodyRequest
            extends HttpServletRequestWrapper {

        private final byte[] body;

        private CachedBodyRequest(
                HttpServletRequest request,
                byte[] body) {
            super(request);
            this.body = body.clone();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return input.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener readListener) {
                    // Synchronous MVC request bodies do not require async callbacks.
                }

                @Override
                public int read() {
                    return input.read();
                }

                @Override
                public int read(byte[] bytes, int offset, int length) {
                    return input.read(bytes, offset, length);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null
                    ? StandardCharsets.UTF_8
                    : Charset.forName(encoding);
            return new BufferedReader(
                    new InputStreamReader(getInputStream(), charset));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
