package com.genersoft.iot.vmp.vmanager.system.log;

import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.authorization.audit.AuthorizationAuditQueryService;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditSummary;
import com.github.pagehelper.PageInfo;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/system/operation-log")
public class SystemOperationLogController {

    private static final String VIEW_PERMISSION = "operations:log:view";

    private final AuthorizationAuditQueryService auditQueryService;
    private final DepartmentScopeService departmentScopeService;
    private final CurrentPrincipalService currentPrincipalService;

    public SystemOperationLogController(
            AuthorizationAuditQueryService auditQueryService,
            DepartmentScopeService departmentScopeService,
            CurrentPrincipalService currentPrincipalService) {
        this.auditQueryService = auditQueryService;
        this.departmentScopeService = departmentScopeService;
        this.currentPrincipalService = currentPrincipalService;
    }

    @PreAuthorize("@authz.require('operations:log:view')")
    @GetMapping
    public PageInfo<AuthorizationAuditSummary> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int count,
            @RequestParam(required = false) String permissionCode,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String principalType,
            @RequestParam(required = false) Long principalId,
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) Long resourceId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        AuthorizationAuditQuery query = new AuthorizationAuditQuery(
                page,
                count,
                permissionCode,
                decision,
                principalType,
                principalId,
                resourceType,
                resourceId,
                startTime,
                endTime);
        return auditQueryService.query(query, decisionScope());
    }

    private DepartmentScope decisionScope() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return departmentScopeService.resolveDecisionScope(principal, VIEW_PERMISSION);
    }
}
