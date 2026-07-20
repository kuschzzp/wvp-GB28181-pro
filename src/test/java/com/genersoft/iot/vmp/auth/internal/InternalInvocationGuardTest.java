package com.genersoft.iot.vmp.auth.internal;

import com.genersoft.iot.vmp.auth.annotation.MediaHookEndpoint;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.user.SystemUserService;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.MediaNodePrincipal;
import com.genersoft.iot.vmp.media.security.MediaNodeType;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.JT1078;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.MEDIA_HOOK;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.REDIS_RPC;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.SCHEDULED;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.SIP;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.AUTHORIZATION_CONFIGURATION;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.BACKGROUND_MAINTENANCE;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.CLUSTER_COORDINATION;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.MEDIA_CALLBACK;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.PROTOCOL_MESSAGE;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.USER_ADMINISTRATION;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class InternalInvocationGuardTest {

    private InternalInvocationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InternalInvocationGuard();
    }

    @AfterEach
    void clearThreadContexts() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
        assertTrue(guard.current().isEmpty(),
                "内部身份必须在入口退出时清理，不能泄漏到复用线程");
    }

    @Test
    void trustedProtocolAndRpcEntriesCreateOnlyMinimalScopedIdentity() {
        try (InternalInvocationGuard.Scope ignored = guard.enterSip("INVITE")) {
            InternalPrincipal principal = guard.require(PROTOCOL_MESSAGE);
            assertEquals(SIP, principal.origin());
            assertEquals("INVITE", principal.sourceId());
            assertEquals(Set.of(PROTOCOL_MESSAGE), principal.allowedUseCases());
            assertTrue(principal.getName().startsWith("internal:sip:"));
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(USER_ADMINISTRATION));
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(AUTHORIZATION_CONFIGURATION));
        }

        try (InternalInvocationGuard.Scope ignored =
                     guard.enterJt1078("terminal:013800138000")) {
            assertEquals(JT1078, guard.require(PROTOCOL_MESSAGE).origin());
        }

        try (InternalInvocationGuard.Scope ignored =
                     guard.enterRedisRpc("wvp-node-2:/stream/close")) {
            assertEquals(REDIS_RPC,
                    guard.require(CLUSTER_COORDINATION).origin());
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(PROTOCOL_MESSAGE));
        }
    }

    @Test
    void scheduledAndMediaEntriesHaveDistinctCapabilities() {
        try (InternalInvocationGuard.Scope ignored =
                     guard.enterScheduled("CleanupTask.run")) {
            InternalPrincipal principal = guard.require(BACKGROUND_MAINTENANCE);
            assertEquals(SCHEDULED, principal.origin());
            assertEquals(Set.of(BACKGROUND_MAINTENANCE),
                    principal.allowedUseCases());
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(MEDIA_CALLBACK));
        }

        MediaNodePrincipal node = authenticatedMediaHookRequest();
        try (InternalInvocationGuard.Scope ignored = guard.enterMediaHook()) {
            InternalPrincipal principal = guard.require(MEDIA_CALLBACK);
            assertEquals(MEDIA_HOOK, principal.origin());
            assertEquals(node.getName(), principal.sourceId());
            assertEquals(Set.of(MEDIA_CALLBACK), principal.allowedUseCases());
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(USER_ADMINISTRATION));
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(AUTHORIZATION_CONFIGURATION));
        }
    }

    @Test
    void ordinaryHttpRequestCannotForgeTrustedOriginFromParameters() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/internal/run");
        request.setParameter("origin", "SIP");
        request.setParameter("sourceId", "trusted-node");
        request.setParameter("mediaServerId", "zlm-1");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        assertThrows(AccessDeniedException.class,
                () -> guard.enterSip(request.getParameter("sourceId")));
        assertThrows(AccessDeniedException.class,
                () -> guard.enterJt1078(request.getParameter("sourceId")));
        assertThrows(AccessDeniedException.class,
                () -> guard.enterRedisRpc(request.getParameter("sourceId")));
        assertThrows(AccessDeniedException.class,
                () -> guard.enterScheduled("ForgedTask.run"));
        assertThrows(AccessDeniedException.class, guard::enterMediaHook);
    }

    @Test
    void mediaHookIdentityMustComeFromAuthenticatedNodeNotRequestText() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/index/hook/zlm/on_publish");
        request.setParameter("mediaServerId", "zlm-1");
        request.setParameter("hookSecret", "attacker-controlled");
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

        AuthPrincipal user = new AuthPrincipal(
                PrincipalType.USER,
                1L,
                1L,
                "admin",
                1L,
                Set.of(1L),
                1L,
                true,
                Set.of("*:*:*"));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        user, null, user.authorities()));
        assertThrows(AccessDeniedException.class, guard::enterMediaHook);

        MediaNodePrincipal node = new MediaNodePrincipal(
                "zlm-1", MediaNodeType.ZLM, 2L);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        node, null, List.of()));
        assertThrows(AccessDeniedException.class, guard::enterMediaHook,
                "只有 SecurityContext 仍不够，还必须来自 Hook 认证过滤器");

        request.setAttribute(
                MediaHookAuthenticationFilter.PRINCIPAL_ATTRIBUTE, node);
        try (InternalInvocationGuard.Scope ignored = guard.enterMediaHook()) {
            assertEquals(node.getName(), guard.require(MEDIA_CALLBACK).sourceId());
        }
    }

    @Test
    void nestedInvocationCannotSwitchOriginOrEscalateCapability() {
        try (InternalInvocationGuard.Scope outer = guard.enterSip("MESSAGE")) {
            try (InternalInvocationGuard.Scope nested = guard.enterSip("MESSAGE")) {
                assertEquals(SIP, guard.require(PROTOCOL_MESSAGE).origin());
            }
            assertThrows(AccessDeniedException.class,
                    () -> guard.enterRedisRpc("wvp-node-2:/device/sync"));
            assertThrows(AccessDeniedException.class,
                    () -> guard.require(CLUSTER_COORDINATION));
        }
    }

    @Test
    void scopeAlwaysClearsAfterFailure() {
        assertThrows(IllegalStateException.class, () -> {
            try (InternalInvocationGuard.Scope ignored = guard.enterSip("BYE")) {
                throw new IllegalStateException("processor failed");
            }
        });
        assertTrue(guard.current().isEmpty());
        assertThrows(AccessDeniedException.class,
                () -> guard.require(PROTOCOL_MESSAGE));
    }

    @Test
    void principalCannotBeConstructedByExternalCodeAndFiltersDoNotUseGuard()
            throws Exception {
        assertTrue(Arrays.stream(InternalPrincipal.class.getDeclaredConstructors())
                .noneMatch(constructor -> Modifier.isPublic(
                        constructor.getModifiers())));

        Path sourceRoot = Path.of("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> offenders = paths
                    .filter(path -> path.getFileName().toString()
                            .endsWith("Filter.java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path)
                                    .contains("InternalInvocationGuard");
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertEquals(List.of(), offenders,
                    "普通 HTTP Filter 不得创建内部身份");
        }
    }

    @Test
    void internalIdentityCannotEnterUserOrResourceAdministrationServices() {
        InternalInvocationAspect aspect = new InternalInvocationAspect(guard);

        SystemUserService userTarget = mock(SystemUserService.class);
        SystemUserService userService = proxy(userTarget, aspect);
        ResourceGroupService groupTarget = mock(ResourceGroupService.class);
        ResourceGroupService groupService = proxy(groupTarget, aspect);
        IUserService legacyTarget = mock(IUserService.class);
        IUserService legacyUserService = proxy(legacyTarget, aspect);

        try (InternalInvocationGuard.Scope ignored = guard.enterSip("REGISTER")) {
            assertThrows(AccessDeniedException.class,
                    () -> userService.query(null, null));
            assertThrows(AccessDeniedException.class, groupService::tree);
            assertThrows(AccessDeniedException.class,
                    () -> legacyUserService.addUser(new User()));

            assertDoesNotThrow(() -> legacyUserService.checkPushAuthority(
                    "call-id", "sign"));
            verify(legacyTarget).checkPushAuthority("call-id", "sign");
        }
    }

    @Test
    void springAspectsEstablishScheduledAndAuthenticatedHookOrigins() {
        InternalInvocationAspect aspect = new InternalInvocationAspect(guard);

        ScheduledProbe scheduledTarget = new ScheduledProbe(guard);
        ScheduledProbe scheduled = classProxy(scheduledTarget, aspect);
        scheduled.run();
        assertEquals(SCHEDULED, scheduledTarget.observed.origin());
        assertEquals(Set.of(BACKGROUND_MAINTENANCE),
                scheduledTarget.observed.allowedUseCases());
        assertTrue(guard.current().isEmpty());

        authenticatedMediaHookRequest();
        MediaHookProbe hookTarget = new MediaHookProbe(guard);
        MediaHookProbe hook = classProxy(hookTarget, aspect);
        hook.onHook();
        assertEquals(MEDIA_HOOK, hookTarget.observed.origin());
        assertEquals(Set.of(MEDIA_CALLBACK),
                hookTarget.observed.allowedUseCases());
        assertTrue(guard.current().isEmpty());
    }

    @Test
    void everyNonSpringTrustedAdapterUsesItsDedicatedGuardEntry()
            throws Exception {
        Map<Path, String> expectedCalls = Map.of(
                Path.of("src/main/java/com/genersoft/iot/vmp/gb28181/"
                        + "transmit/SIPProcessorObserver.java"),
                "internalInvocationGuard.enterSip(",
                Path.of("src/main/java/com/genersoft/iot/vmp/jt1078/"
                        + "codec/decode/Jt808Decoder.java"),
                "internalInvocationGuard.enterJt1078(",
                Path.of("src/main/java/com/genersoft/iot/vmp/conf/redis/"
                        + "RedisRpcConfig.java"),
                "internalInvocationGuard.enterRedisRpc(");

        for (Map.Entry<Path, String> expected : expectedCalls.entrySet()) {
            assertTrue(Files.readString(expected.getKey())
                            .contains(expected.getValue()),
                    expected.getKey() + " 必须在受信入口建立内部身份");
        }

        Path sourceRoot = Path.of("src/main/java");
        try (var paths = Files.walk(sourceRoot)) {
            List<Path> principalFactories = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            return Files.readString(path)
                                    .contains("new InternalPrincipal(");
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertEquals(
                    List.of(Path.of("src/main/java/com/genersoft/iot/vmp/"
                            + "auth/internal/InternalInvocationGuard.java")),
                    principalFactories,
                    "InternalPrincipal 只能由受控工厂构造");
        }
    }

    private MediaNodePrincipal authenticatedMediaHookRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/index/hook/zlm/on_publish");
        MediaNodePrincipal node = new MediaNodePrincipal(
                "zlm-1", MediaNodeType.ZLM, 2L);
        request.setAttribute(
                MediaHookAuthenticationFilter.PRINCIPAL_ATTRIBUTE, node);
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        node, null, List.of()));
        return node;
    }

    @SuppressWarnings("unchecked")
    private <T> T proxy(T target, InternalInvocationAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    @SuppressWarnings("unchecked")
    private <T> T classProxy(T target, InternalInvocationAspect aspect) {
        AspectJProxyFactory factory = new AspectJProxyFactory(target);
        factory.setProxyTargetClass(true);
        factory.addAspect(aspect);
        return (T) factory.getProxy();
    }

    static class ScheduledProbe {

        private final InternalInvocationGuard guard;
        private InternalPrincipal observed;

        ScheduledProbe(InternalInvocationGuard guard) {
            this.guard = guard;
        }

        @Scheduled(fixedDelay = 1_000)
        public void run() {
            observed = guard.require(BACKGROUND_MAINTENANCE);
        }
    }

    @MediaHookEndpoint(reason = "测试媒体 Hook 内部身份边界")
    static class MediaHookProbe {

        private final InternalInvocationGuard guard;
        private InternalPrincipal observed;

        MediaHookProbe(InternalInvocationGuard guard) {
            this.guard = guard;
        }

        public void onHook() {
            observed = guard.require(MEDIA_CALLBACK);
        }
    }
}
