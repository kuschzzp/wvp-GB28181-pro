package com.genersoft.iot.vmp.auth.service;

import java.util.Set;

public interface RoleAssignmentService {

    void assignRoles(long operatorId, long userId, Set<Long> roleIds);
}
