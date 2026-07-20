package com.genersoft.iot.vmp.auth.role;

import com.genersoft.iot.vmp.auth.dto.RoleRequest;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.menu.SystemMenuService;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.role.dto.ChangeRoleStatusRequest;
import com.genersoft.iot.vmp.auth.role.dto.SystemRoleDetail;
import com.genersoft.iot.vmp.auth.role.dto.SystemRoleReferenceData;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.service.IRoleService;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
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
@RequestMapping("/api/system/role")
public class SystemRoleController {

    private final IRoleService roleService;
    private final SystemMenuService menuService;
    private final DepartmentTreeService departmentTreeService;
    private final CurrentPrincipalService currentPrincipalService;

    public SystemRoleController(
            IRoleService roleService,
            SystemMenuService menuService,
            DepartmentTreeService departmentTreeService,
            CurrentPrincipalService currentPrincipalService) {
        this.roleService = roleService;
        this.menuService = menuService;
        this.departmentTreeService = departmentTreeService;
        this.currentPrincipalService = currentPrincipalService;
    }

    @PreAuthorize("@authz.require('system:role:list')")
    @GetMapping
    public List<Role> list() {
        return roleService.getAll();
    }

    @PreAuthorize("@authz.require('system:role:list')")
    @GetMapping("/references")
    public SystemRoleReferenceData references() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        long userId = principal.userId() == null ? principal.id() : principal.userId();
        return new SystemRoleReferenceData(
                menuService.list(),
                departmentTreeService.tree(DepartmentScope.all(userId)));
    }

    @PreAuthorize("@authz.require('system:role:list')")
    @GetMapping("/{id}")
    public SystemRoleDetail detail(@PathVariable long id) {
        Role role = roleService.getRoleById(id);
        if (role == null) {
            throw new ControllerException(404, "角色不存在");
        }
        return new SystemRoleDetail(
                role,
                roleService.getMenuIds(id),
                roleService.getDepartmentIds(id),
                roleService.getResourceGroupIds(id));
    }

    @PreAuthorize("@authz.require('system:role:add')")
    @PostMapping
    public ResponseEntity<Void> create(@Valid @RequestBody RoleRequest request) {
        if (request.id() != null) {
            throw new IllegalArgumentException("新增角色不能指定 ID");
        }
        roleService.saveRole(
                request.toCommand(null),
                request.menuIds(),
                request.deptIds(),
                request.resourceGroupIds());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:role:edit')")
    @PutMapping("/{id}")
    public ResponseEntity<Void> update(
            @PathVariable long id,
            @Valid @RequestBody RoleRequest request) {
        roleService.saveRole(
                request.toCommand(id),
                request.menuIds(),
                request.deptIds(),
                request.resourceGroupIds());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:role:changeStatus')")
    @PutMapping("/{id}/status")
    public ResponseEntity<Void> changeStatus(
            @PathVariable long id,
            @Valid @RequestBody ChangeRoleStatusRequest request) {
        roleService.changeStatus(id, request.status());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:role:remove')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        roleService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
