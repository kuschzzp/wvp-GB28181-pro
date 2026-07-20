package com.genersoft.iot.vmp.auth.service;

import java.util.Collection;

public interface TokenVersionService {

    void bumpUser(long userId, String reason);

    void bumpUsers(Collection<Long> userIds, String reason);

    void bumpUsersByRoleId(long roleId, String reason);

    boolean matches(long userId, long tokenVersion);
}
