package com.genersoft.iot.vmp.auth.internal;

import com.genersoft.iot.vmp.auth.annotation.MediaHookEndpoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;

import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.AUTHORIZATION_CONFIGURATION;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.USER_ADMINISTRATION;

/**
 * Establishes internal identities for Spring-managed entry points and blocks
 * those identities at human administration/configuration use cases.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
public class InternalInvocationAspect {

    private final InternalInvocationGuard guard;

    public InternalInvocationAspect(InternalInvocationGuard guard) {
        this.guard = Objects.requireNonNull(guard, "guard must not be null");
    }

    @Around("@within(mediaHookEndpoint) || @annotation(mediaHookEndpoint)")
    public Object aroundMediaHook(
            ProceedingJoinPoint joinPoint,
            MediaHookEndpoint mediaHookEndpoint) throws Throwable {
        try (InternalInvocationGuard.Scope ignored = guard.enterMediaHook()) {
            return joinPoint.proceed();
        }
    }

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object aroundScheduled(ProceedingJoinPoint joinPoint)
            throws Throwable {
        String sourceId = joinPoint.getSignature().getDeclaringType()
                .getSimpleName() + "." + joinPoint.getSignature().getName();
        try (InternalInvocationGuard.Scope ignored =
                     guard.enterScheduled(sourceId)) {
            return joinPoint.proceed();
        }
    }

    @Around("target(com.genersoft.iot.vmp.auth.user.SystemUserService)"
            + " || target(com.genersoft.iot.vmp.auth.service.SystemUserService)"
            + " || target(com.genersoft.iot.vmp.auth.service.RoleAssignmentService)"
            + " || target(com.genersoft.iot.vmp.service.IUserApiKeyService)")
    public Object rejectInternalUserAdministration(
            ProceedingJoinPoint joinPoint) throws Throwable {
        guard.rejectIfInternal(USER_ADMINISTRATION);
        return joinPoint.proceed();
    }

    @Around("target(com.genersoft.iot.vmp.service.IUserService) && ("
            + "execution(* addUser(..))"
            + " || execution(* softDelete(..))"
            + " || execution(* getAllUsers(..))"
            + " || execution(* getUsers(..))"
            + " || execution(* changePushKey(..))"
            + " || execution(* upgradePassword(..))"
            + " || execution(* updateLastLogin(..)))")
    public Object rejectInternalLegacyUserAdministration(
            ProceedingJoinPoint joinPoint) throws Throwable {
        guard.rejectIfInternal(USER_ADMINISTRATION);
        return joinPoint.proceed();
    }

    @Around("target(com.genersoft.iot.vmp.service.IRoleService)"
            + " || target(com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy)"
            + " || target(com.genersoft.iot.vmp.auth.department.DepartmentTreeService)"
            + " || target(com.genersoft.iot.vmp.auth.menu.SystemMenuService)"
            + " || target(com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService)"
            + " || target(com.genersoft.iot.vmp.auth.apikey.ApiKeyScopeService)")
    public Object rejectInternalAuthorizationConfiguration(
            ProceedingJoinPoint joinPoint) throws Throwable {
        guard.rejectIfInternal(AUTHORIZATION_CONFIGURATION);
        return joinPoint.proceed();
    }
}
