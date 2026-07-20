package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final TokenVersionService tokenVersionService;
    private final TokenRevocationService tokenRevocationService;
    private final IUserService userService;
    private final PermissionRepository permissionRepository;
    private final PermissionService permissionService;
    private final UserSetting userSetting;
    private final AuthenticationEntryPoint authenticationEntryPoint;

    public JwtAuthenticationFilter(
            JwtUtils jwtUtils,
            TokenVersionService tokenVersionService,
            TokenRevocationService tokenRevocationService,
            IUserService userService,
            PermissionRepository permissionRepository,
            PermissionService permissionService,
            UserSetting userSetting,
            AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
        this.jwtUtils = jwtUtils;
        this.tokenVersionService = tokenVersionService;
        this.tokenRevocationService = tokenRevocationService;
        this.userService = userService;
        this.permissionRepository = permissionRepository;
        this.permissionService = permissionService;
        this.userSetting = userSetting;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        if (isDisabledDocumentationRequest(request)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        if (hasText(request.getHeader(JwtUtils.HEADER))
                && hasText(request.getHeader(JwtUtils.API_KEY_HEADER))) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(
                    request,
                    response,
                    new InvalidAccessTokenException("不能同时提供 access-token 和 api-key"));
            return;
        }

        String tokenValue = request.getHeader(JwtUtils.HEADER);
        if (tokenValue == null || tokenValue.isBlank()) {
            chain.doFilter(request, response);
            return;
        }

        try {
            authenticate(tokenValue);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            InvalidAccessTokenException authenticationException = exception instanceof InvalidAccessTokenException invalid
                    ? invalid
                    : new InvalidAccessTokenException("访问令牌校验失败", exception);
            authenticationEntryPoint.commence(request, response, authenticationException);
            return;
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String tokenValue) {
        VerifiedAccessToken token = jwtUtils.verifyAccessToken(tokenValue);
        if (tokenRevocationService.isRevoked(token.jti())) {
            throw new InvalidAccessTokenException("访问令牌已退出");
        }
        if (!tokenVersionService.matches(token.userId(), token.tokenVersion())) {
            throw new InvalidAccessTokenException("访问令牌版本已失效");
        }
        User user = requireActiveUser(token.userId());
        Set<Long> roleIds = Set.copyOf(permissionRepository.findEnabledRoleIds(user.getId()));
        Set<String> permissions = permissionService.resolvePermissions(user.getId(), token.tokenVersion());
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.USER,
                user.getId(),
                user.getId(),
                user.getUsername(),
                user.getDeptId(),
                roleIds,
                token.tokenVersion(),
                permissionRepository.isSystemUser(user.getId()),
                permissions);
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.authorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

    private User requireActiveUser(long userId) {
        User user = userService.getUserById(userId);
        if (user == null || user.isDeleted() || user.getStatus() != 0 || user.isLocked()) {
            throw new InvalidAccessTokenException("用户状态已失效");
        }
        return user;
    }

    private boolean isDisabledDocumentationRequest(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return !Boolean.TRUE.equals(userSetting.getDocEnable())
                && (uri.startsWith("/doc.html")
                || uri.startsWith("/swagger-ui")
                || uri.startsWith("/swagger-resources")
                || uri.startsWith("/v3/api-docs")
                || uri.startsWith("/webjars/"));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
