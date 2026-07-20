package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.regex.Pattern;

@Service("authz")
@Slf4j
public class PermissionEnforcerImpl implements PermissionEnforcer {

    private static final Pattern PERMISSION_CODE = Pattern.compile(
            "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9-]*$");

    private final PermissionService permissionService;
    private final CurrentPrincipalService currentPrincipalService;
    private final AuthorizationAuditSink auditSink;
    private final AuthorizationMode mode;

    public PermissionEnforcerImpl(
            PermissionService permissionService,
            CurrentPrincipalService currentPrincipalService,
            AuthorizationAuditSink auditSink,
            AuthorizationProperties properties,
            Environment environment) {
        this.permissionService = permissionService;
        this.currentPrincipalService = currentPrincipalService;
        this.auditSink = auditSink;
        this.mode = properties.mode();
        validateModeForEnvironment(environment);
    }

    @Override
    public boolean check(String permissionCode) {
        boolean allowed = evaluate(permissionCode);
        return switch (mode) {
            case DISABLED, AUDIT -> true;
            case ENFORCE -> allowed;
        };
    }

    @Override
    public boolean require(String permissionCode) {
        return evaluate(permissionCode);
    }

    private boolean evaluate(String permissionCode) {
        requirePermissionCode(permissionCode);
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        boolean allowed = permissionService.hasPermission(principal, permissionCode);
        if (mode != AuthorizationMode.DISABLED) {
            auditSink.record(new AuthzAuditRecord(
                    principal.type(),
                    principal.id(),
                    principal.deptId(),
                    permissionCode,
                    null,
                    null,
                    allowed,
                    allowed ? "PERMISSION_GRANTED" : "PERMISSION_MISSING",
                    null,
                    null,
                    LocalDateTime.now()));
        }
        return allowed;
    }

    private void requirePermissionCode(String permissionCode) {
        if (permissionCode == null || !PERMISSION_CODE.matcher(permissionCode).matches()) {
            throw new IllegalArgumentException("非法权限编码: " + permissionCode);
        }
    }

    private void validateModeForEnvironment(Environment environment) {
        boolean developmentProfile = Arrays.stream(environment.getActiveProfiles())
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("dev")
                        || profile.endsWith("-dev")
                        || profile.equals("test")
                        || profile.endsWith("-test")
                        || profile.equals("local"));
        if (mode == AuthorizationMode.DISABLED && !developmentProfile) {
            throw new IllegalStateException("authorization.mode=disabled 仅允许 dev/test/local 环境");
        }
        if (mode == AuthorizationMode.AUDIT && !developmentProfile) {
            log.warn("authorization.mode=audit 运行于非开发环境：功能权限（@authz.check）仅审计不拦截，"
                    + "对象级授权、system:* 管理端点与身份认证仍然强制；正式权限隔离前请按 2.8.0 授权灰度手册切换为 enforce");
        }
    }
}
