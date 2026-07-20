package com.genersoft.iot.vmp.auth.internal;

import java.security.Principal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Credential-free identity for one trusted, non-user invocation.
 *
 * <p>The constructor is deliberately package-private. Only
 * {@link InternalInvocationGuard} may create this principal, and every
 * instance is limited to the use cases assigned to its trusted origin.</p>
 */
public final class InternalPrincipal implements Principal {

    public enum Origin {
        SIP,
        JT1078,
        REDIS_RPC,
        MEDIA_HOOK,
        SCHEDULED
    }

    public enum UseCase {
        PROTOCOL_MESSAGE,
        CLUSTER_COORDINATION,
        MEDIA_CALLBACK,
        BACKGROUND_MAINTENANCE,
        USER_ADMINISTRATION,
        AUTHORIZATION_CONFIGURATION
    }

    private final UUID invocationId;
    private final Origin origin;
    private final String sourceId;
    private final Instant issuedAt;
    private final Set<UseCase> allowedUseCases;

    InternalPrincipal(
            UUID invocationId,
            Origin origin,
            String sourceId,
            Instant issuedAt,
            Set<UseCase> allowedUseCases) {
        this.invocationId = Objects.requireNonNull(
                invocationId, "invocationId must not be null");
        this.origin = Objects.requireNonNull(origin, "origin must not be null");
        if (sourceId == null || sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        this.sourceId = sourceId;
        this.issuedAt = Objects.requireNonNull(
                issuedAt, "issuedAt must not be null");
        this.allowedUseCases = Set.copyOf(Objects.requireNonNull(
                allowedUseCases, "allowedUseCases must not be null"));
        if (this.allowedUseCases.isEmpty()) {
            throw new IllegalArgumentException(
                    "allowedUseCases must not be empty");
        }
        if (this.allowedUseCases.contains(UseCase.USER_ADMINISTRATION)
                || this.allowedUseCases.contains(
                UseCase.AUTHORIZATION_CONFIGURATION)) {
            throw new IllegalArgumentException(
                    "internal principal cannot receive administration capabilities");
        }
    }

    public UUID invocationId() {
        return invocationId;
    }

    public Origin origin() {
        return origin;
    }

    public String sourceId() {
        return sourceId;
    }

    public Instant issuedAt() {
        return issuedAt;
    }

    public Set<UseCase> allowedUseCases() {
        return allowedUseCases;
    }

    public boolean allows(UseCase useCase) {
        return allowedUseCases.contains(useCase);
    }

    @Override
    public String getName() {
        return "internal:"
                + origin.name().toLowerCase(Locale.ROOT)
                + ":"
                + sourceId;
    }

    @Override
    public String toString() {
        return "InternalPrincipal{" + getName()
                + ", invocationId=" + invocationId + '}';
    }
}
