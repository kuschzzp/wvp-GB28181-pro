package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.dto.CreateDepartmentRequest;
import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeNode;
import com.genersoft.iot.vmp.auth.department.dto.UpdateDepartmentRequest;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/dept")
public class DepartmentController {

    private final DepartmentTreeService departmentTreeService;
    private final DepartmentScopeService departmentScopeService;
    private final CurrentPrincipalService currentPrincipalService;

    public DepartmentController(
            DepartmentTreeService departmentTreeService,
            DepartmentScopeService departmentScopeService,
            CurrentPrincipalService currentPrincipalService) {
        this.departmentTreeService = departmentTreeService;
        this.departmentScopeService = departmentScopeService;
        this.currentPrincipalService = currentPrincipalService;
    }

    @PreAuthorize("@authz.require('system:dept:list')")
    @GetMapping("/tree")
    public List<DepartmentTreeNode> tree() {
        return departmentTreeService.tree(decisionScope("system:dept:list"));
    }

    @PreAuthorize("@authz.require('system:dept:add')")
    @PostMapping
    public long create(@Valid @RequestBody CreateDepartmentRequest request) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        DepartmentScope scope = departmentScopeService.resolveDecisionScope(
                principal, "system:dept:add");
        return departmentTreeService.create(request.toCommand(), principal, scope);
    }

    @PreAuthorize("@authz.require('system:dept:edit')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable long id,
            @Valid @RequestBody UpdateDepartmentRequest request) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        DepartmentScope scope = departmentScopeService.resolveDecisionScope(
                principal, "system:dept:edit");
        departmentTreeService.update(request.toCommand(id), principal, scope);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:dept:remove')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        DepartmentScope scope = departmentScopeService.resolveDecisionScope(
                principal, "system:dept:remove");
        departmentTreeService.delete(id, principal, scope);
        return ResponseEntity.noContent().build();
    }

    private DepartmentScope decisionScope(String permissionCode) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return departmentScopeService.resolveDecisionScope(principal, permissionCode);
    }
}
