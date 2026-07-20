package com.genersoft.iot.vmp.conf.websocket;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public record LogWebSocketTicketClaims(
        PrincipalType principalType,
        long principalId,
        long userId,
        String username,
        Long deptId,
        Set<Long> roleIds,
        long tokenVersion,
        boolean systemUser,
        Set<String> permissions,
        Instant issuedAt,
        Instant expiresAt) {

    public LogWebSocketTicketClaims {
        principalType = Objects.requireNonNull(
                principalType, "principalType must not be null");
        username = requireText(username, "username");
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt must not be null");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt must not be null");
        if (principalType != PrincipalType.USER
                || principalId <= 0
                || userId <= 0
                || tokenVersion < 0) {
            throw new IllegalArgumentException("log websocket principal is invalid");
        }
        if (!systemUser
                && !permissions.contains(LogWebSocketTicketService.LOG_VIEW_PERMISSION)) {
            throw new IllegalArgumentException("log websocket permission is missing");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("expiresAt must be after issuedAt");
        }
    }

    public static LogWebSocketTicketClaims from(
            AuthPrincipal principal,
            Instant issuedAt,
            Instant expiresAt) {
        return new LogWebSocketTicketClaims(
                principal.type(),
                principal.id(),
                principal.userId(),
                principal.username(),
                principal.deptId(),
                principal.roleIds(),
                principal.tokenVersion(),
                principal.systemUser(),
                principal.permissions(),
                issuedAt,
                expiresAt);
    }

    public AuthPrincipal toPrincipal() {
        return new AuthPrincipal(
                principalType,
                principalId,
                userId,
                username,
                deptId,
                roleIds,
                tokenVersion,
                systemUser,
                permissions);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.trim();
    }
}
