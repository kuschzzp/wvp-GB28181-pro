package com.genersoft.iot.vmp.media.security;

import java.security.Principal;
import java.util.Objects;

/**
 * Authenticated media node identity. It never represents a platform user.
 */
public record MediaNodePrincipal(
        String mediaServerId,
        MediaNodeType nodeType,
        long hookSecretVersion) implements Principal {

    public MediaNodePrincipal {
        if (mediaServerId == null || mediaServerId.isBlank()) {
            throw new IllegalArgumentException("mediaServerId must not be blank");
        }
        mediaServerId = mediaServerId.trim();
        nodeType = Objects.requireNonNull(nodeType, "nodeType must not be null");
        if (hookSecretVersion <= 0) {
            throw new IllegalArgumentException("hookSecretVersion must be positive");
        }
    }

    @Override
    public String getName() {
        return nodeType.name().toLowerCase() + ":" + mediaServerId;
    }
}
