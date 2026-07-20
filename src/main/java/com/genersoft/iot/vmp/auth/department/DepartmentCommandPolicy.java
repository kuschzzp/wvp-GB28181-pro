package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;

import java.util.Set;

public interface DepartmentCommandPolicy {

    void requireUserInScope(long targetUserId, DepartmentScope scope);

    void requireDepartmentInScope(long targetDeptId, DepartmentScope scope);

    void requireRoleAssignmentAllowed(AuthPrincipal operator, Set<Long> roleIds);
}
