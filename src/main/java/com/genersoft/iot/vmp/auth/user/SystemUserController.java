package com.genersoft.iot.vmp.auth.user;

import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.dto.AssignRolesRequest;
import com.genersoft.iot.vmp.auth.dto.CreateUserRequest;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.user.dto.AdminUpdateUserRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeUserStatusRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeOwnPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.ResetPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.SystemUserReferenceData;
import com.genersoft.iot.vmp.auth.user.dto.UpdateOwnProfileRequest;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.auth.user.dto.UserSummary;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.service.IRoleService;
import com.github.pagehelper.PageInfo;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/system/user")
public class SystemUserController {

    private final SystemUserService userService;
    private final DepartmentScopeService departmentScopeService;
    private final CurrentPrincipalService currentPrincipalService;
    private final DepartmentTreeService departmentTreeService;
    private final IRoleService roleService;

    public SystemUserController(
            SystemUserService userService,
            DepartmentScopeService departmentScopeService,
            CurrentPrincipalService currentPrincipalService,
            DepartmentTreeService departmentTreeService,
            IRoleService roleService) {
        this.userService = userService;
        this.departmentScopeService = departmentScopeService;
        this.currentPrincipalService = currentPrincipalService;
        this.departmentTreeService = departmentTreeService;
        this.roleService = roleService;
    }

    @PreAuthorize("@authz.require('system:user:list')")
    @GetMapping
    public PageInfo<UserSummary> query(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int count,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) Integer status) {
        return userService.query(
                new UserQuery(page, count, keyword, deptId, status),
                decisionScope("system:user:list"));
    }

    @PreAuthorize("@authz.require('system:user:list')")
    @GetMapping("/references")
    public SystemUserReferenceData references() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        DepartmentScope scope = departmentScopeService.resolveDecisionScope(
                principal, "system:user:list");
        List<com.genersoft.iot.vmp.storager.dao.dto.Role> assignableRoles = roleService.getAll()
                .stream()
                .filter(role -> principal.systemUser() || !role.isSystem())
                .toList();
        return new SystemUserReferenceData(
                departmentTreeService.tree(scope),
                assignableRoles);
    }

    @PreAuthorize("@authz.require('system:user:list')")
    @GetMapping("/{id}")
    public UserSummary detail(@PathVariable long id) {
        return userService.findVisible(id, decisionScope("system:user:list"))
                .orElseThrow(() -> new ControllerException(404, "用户不存在"));
    }

    @PreAuthorize("@authz.require('system:user:add')")
    @PostMapping
    public long create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(request, decisionScope("system:user:add"));
    }

    @PreAuthorize("@authz.require('system:user:edit')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable long id,
            @Valid @RequestBody AdminUpdateUserRequest request) {
        userService.update(id, request, decisionScope("system:user:edit"));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:user:changeStatus')")
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        userService.changeStatus(id, request, decisionScope("system:user:changeStatus"));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:role:assign')")
    @PutMapping("/{id}/roles")
    public ResponseEntity<Void> assignRoles(
            @PathVariable long id,
            @Valid @RequestBody AssignRolesRequest request) {
        userService.assignRoles(id, request.roleIds(), decisionScope("system:role:assign"));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:user:resetPwd')")
    @PutMapping("/{id}/password")
    public ResponseEntity<Void> resetPassword(
            @PathVariable long id,
            @Valid @RequestBody ResetPasswordRequest request) {
        userService.resetPassword(id, request, decisionScope("system:user:resetPwd"));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:user:remove')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        userService.delete(id, decisionScope("system:user:remove"));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/profile")
    public ResponseEntity<Void> updateOwnProfile(
            @Valid @RequestBody UpdateOwnProfileRequest request) {
        userService.updateOwnProfile(request);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("isAuthenticated()")
    @PutMapping("/profile/password")
    public ResponseEntity<Void> changeOwnPassword(
            @Valid @RequestBody ChangeOwnPasswordRequest request) {
        userService.changeOwnPassword(request);
        return ResponseEntity.noContent().build();
    }

    private DepartmentScope decisionScope(String permissionCode) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return departmentScopeService.resolveDecisionScope(principal, permissionCode);
    }
}
