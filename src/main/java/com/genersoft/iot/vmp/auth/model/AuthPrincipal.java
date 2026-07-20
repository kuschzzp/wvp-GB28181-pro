package com.genersoft.iot.vmp.auth.model;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.security.Principal;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * Security identity used after authentication. Credentials must never be added to this type.
 */
public record AuthPrincipal(
        PrincipalType type,
        long id,
        Long userId,
        String username,
        Long deptId,
        Set<Long> roleIds,
        long tokenVersion,
        boolean systemUser,
        Set<String> permissions) implements Principal {

    public AuthPrincipal {
        type = Objects.requireNonNull(type, "type must not be null");
        username = Objects.requireNonNull(username, "username must not be null");
        roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        if (type == PrincipalType.USER && userId == null) {
            throw new IllegalArgumentException("user principal requires userId");
        }
    }

    @Override
    public String getName() {
        return username;
    }

    public Collection<? extends GrantedAuthority> authorities() {
        return permissions.stream()
                .sorted()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
