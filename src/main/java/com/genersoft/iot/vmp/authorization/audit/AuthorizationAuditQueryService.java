package com.genersoft.iot.vmp.authorization.audit;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditSummary;
import com.github.pagehelper.PageInfo;

public interface AuthorizationAuditQueryService {

    PageInfo<AuthorizationAuditSummary> query(
            AuthorizationAuditQuery query,
            DepartmentScope scope);
}
