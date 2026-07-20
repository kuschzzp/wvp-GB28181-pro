package com.genersoft.iot.vmp.authorization.audit.impl;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.authorization.audit.AuthorizationAuditQueryService;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditSummary;
import com.genersoft.iot.vmp.storager.dao.AuthzAuditMapper;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class AuthorizationAuditQueryServiceImpl implements AuthorizationAuditQueryService {

    private final AuthzAuditMapper auditMapper;

    public AuthorizationAuditQueryServiceImpl(AuthzAuditMapper auditMapper) {
        this.auditMapper = auditMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PageInfo<AuthorizationAuditSummary> query(
            AuthorizationAuditQuery query,
            DepartmentScope scope) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        PageHelper.startPage(query.page(), query.count());
        List<AuthorizationAuditSummary> records = auditMapper.query(query, scope);
        return new PageInfo<>(records);
    }
}
