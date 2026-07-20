package com.genersoft.iot.vmp.auth.repository;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

public interface PermissionCache {

    Optional<Set<String>> get(long userId, long tokenVersion);

    void put(long userId, long tokenVersion, Set<String> permissions, Duration ttl);

    void evictUser(long userId);
}
