package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;

public interface DepartmentScopeService {

    DepartmentScope resolveDecisionScope(AuthPrincipal principal, String permissionCode);

    DepartmentScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode);
}
