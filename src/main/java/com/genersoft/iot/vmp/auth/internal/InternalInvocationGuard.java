package com.genersoft.iot.vmp.auth.internal;

import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.MediaNodePrincipal;
import com.genersoft.iot.vmp.media.security.MediaNodeType;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.JT1078;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.MEDIA_HOOK;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.REDIS_RPC;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.SCHEDULED;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.Origin.SIP;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.BACKGROUND_MAINTENANCE;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.CLUSTER_COORDINATION;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.MEDIA_CALLBACK;
import static com.genersoft.iot.vmp.auth.internal.InternalPrincipal.UseCase.PROTOCOL_MESSAGE;

/**
 * Creates and verifies short-lived internal identities at trusted adapters.
 *
 * <p>The identity lives only for the current synchronous invocation. It is
 * never reconstructed from an HTTP parameter and is not propagated to an
 * executor automatically.</p>
 */
@Component
public final class InternalInvocationGuard {

    private static final int MAX_SOURCE_ID_LENGTH = 160;

    private final ThreadLocal<InvocationState> current = new ThreadLocal<>();

    public Scope enterSip(String sourceId) {
        return enterNonHttp(SIP, sourceId, Set.of(PROTOCOL_MESSAGE));
    }

    public Scope enterJt1078(String sourceId) {
        return enterNonHttp(JT1078, sourceId, Set.of(PROTOCOL_MESSAGE));
    }

    public Scope enterRedisRpc(String sourceId) {
        return enterNonHttp(
                REDIS_RPC, sourceId, Set.of(CLUSTER_COORDINATION));
    }

    Scope enterScheduled(String sourceId) {
        return enterNonHttp(
                SCHEDULED, sourceId, Set.of(BACKGROUND_MAINTENANCE));
    }

    Scope enterMediaHook() {
        ServletRequestAttributes requestAttributes = servletRequestAttributes();
        if (requestAttributes == null) {
            throw denied("媒体 Hook 内部身份只能在已认证的 HTTP Hook 中创建");
        }

        HttpServletRequest request = requestAttributes.getRequest();
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal()
                instanceof MediaNodePrincipal nodePrincipal)) {
            throw denied("媒体 Hook 缺少已认证的媒体节点身份");
        }

        Object requestPrincipal = request.getAttribute(
                MediaHookAuthenticationFilter.PRINCIPAL_ATTRIBUTE);
        if (!nodePrincipal.equals(requestPrincipal)
                || !matchesHookPath(request, nodePrincipal.nodeType())) {
            throw denied("媒体 Hook 身份与请求边界不一致");
        }

        return enter(
                MEDIA_HOOK,
                nodePrincipal.getName(),
                Set.of(MEDIA_CALLBACK));
    }

    public Optional<InternalPrincipal> current() {
        InvocationState state = current.get();
        return state == null
                ? Optional.empty()
                : Optional.of(state.principal);
    }

    public InternalPrincipal require(InternalPrincipal.UseCase useCase) {
        Objects.requireNonNull(useCase, "useCase must not be null");
        InvocationState state = current.get();
        if (state == null) {
            throw denied("当前调用没有受信内部身份");
        }
        if (!state.principal.allows(useCase)) {
            throw denied("内部来源 " + state.principal.origin()
                    + " 无权执行 " + useCase);
        }
        return state.principal;
    }

    void rejectIfInternal(InternalPrincipal.UseCase prohibitedUseCase) {
        Objects.requireNonNull(
                prohibitedUseCase, "prohibitedUseCase must not be null");
        InvocationState state = current.get();
        if (state != null) {
            throw denied("内部来源 " + state.principal.origin()
                    + " 不得进入 " + prohibitedUseCase);
        }
    }

    private Scope enterNonHttp(
            InternalPrincipal.Origin origin,
            String sourceId,
            Set<InternalPrincipal.UseCase> allowedUseCases) {
        RequestAttributes requestAttributes =
                RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            throw denied("普通 HTTP 请求不能创建非 HTTP 内部身份");
        }
        return enter(origin, sourceId, allowedUseCases);
    }

    private Scope enter(
            InternalPrincipal.Origin origin,
            String sourceId,
            Set<InternalPrincipal.UseCase> allowedUseCases) {
        String normalizedSourceId = normalizeSourceId(sourceId);
        InvocationState existing = current.get();
        if (existing != null) {
            InternalPrincipal principal = existing.principal;
            if (principal.origin() != origin
                    || !principal.sourceId().equals(normalizedSourceId)
                    || !principal.allowedUseCases().equals(allowedUseCases)) {
                throw denied("内部调用不能切换来源或扩大能力");
            }
            existing.depth++;
            return new Scope(this, existing, existing.depth);
        }

        InternalPrincipal principal = new InternalPrincipal(
                UUID.randomUUID(),
                origin,
                normalizedSourceId,
                Instant.now(),
                allowedUseCases);
        InvocationState state = new InvocationState(principal);
        current.set(state);
        return new Scope(this, state, 1);
    }

    private String normalizeSourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("内部调用 sourceId 不能为空");
        }
        String normalized = sourceId.trim();
        if (normalized.length() > MAX_SOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("内部调用 sourceId 过长");
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw new IllegalArgumentException(
                        "内部调用 sourceId 包含控制字符");
            }
        }
        return normalized;
    }

    private ServletRequestAttributes servletRequestAttributes() {
        RequestAttributes attributes =
                RequestContextHolder.getRequestAttributes();
        return attributes instanceof ServletRequestAttributes servlet
                ? servlet
                : null;
    }

    private boolean matchesHookPath(
            HttpServletRequest request,
            MediaNodeType nodeType) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath != null
                && !contextPath.isBlank()
                && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
        return switch (nodeType) {
            case ZLM -> path.startsWith("/index/hook/zlm/");
            case ABL -> path.startsWith("/index/hook/abl/");
        };
    }

    private void exit(
            InvocationState expectedState,
            int expectedDepth,
            Thread owner) {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException(
                    "内部调用 scope 必须在创建线程关闭");
        }
        InvocationState active = current.get();
        if (active != expectedState || active.depth != expectedDepth) {
            current.remove();
            throw new IllegalStateException(
                    "内部调用 scope 关闭顺序无效，身份已清理");
        }
        active.depth--;
        if (active.depth == 0) {
            current.remove();
        }
    }

    private AccessDeniedException denied(String message) {
        return new AccessDeniedException(message);
    }

    private static final class InvocationState {

        private final InternalPrincipal principal;
        private int depth = 1;

        private InvocationState(InternalPrincipal principal) {
            this.principal = principal;
        }
    }

    public static final class Scope implements AutoCloseable {

        private final InternalInvocationGuard guard;
        private final InvocationState state;
        private final int depth;
        private final Thread owner;
        private boolean closed;

        private Scope(
                InternalInvocationGuard guard,
                InvocationState state,
                int depth) {
            this.guard = guard;
            this.state = state;
            this.depth = depth;
            this.owner = Thread.currentThread();
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            guard.exit(state, depth, owner);
            closed = true;
        }
    }
}
