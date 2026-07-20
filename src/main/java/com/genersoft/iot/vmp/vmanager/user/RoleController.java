package com.genersoft.iot.vmp.vmanager.user;

import com.genersoft.iot.vmp.auth.dto.RoleCommand;
import com.genersoft.iot.vmp.auth.dto.RoleRequest;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IRoleService;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@Tag(name = "角色管理")
@RestController
@RequestMapping("/api/role")
public class RoleController {

    private final IRoleService roleService;

    public RoleController(IRoleService roleService) {
        this.roleService = roleService;
    }

    @PreAuthorize("@authz.require('system:role:add')")
    @PostMapping
    @Operation(summary = "新增角色", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void add(@Valid @RequestBody RoleRequest request) {
        roleService.saveRole(
                request.toCommand(),
                request.menuIds(),
                request.deptIds(),
                request.resourceGroupIds());
    }

    @PreAuthorize("@authz.require('system:role:add')")
    @PostMapping("/add")
    @Operation(summary = "新增角色（旧版兼容）", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void addLegacy(
            @RequestParam String name,
            @RequestParam(required = false) String authority) {
        String roleKey = authority != null && authority.matches("^[a-z][a-z0-9_]{1,63}$")
                ? authority
                : "role_" + Long.toUnsignedString(System.nanoTime(), 36);
        roleService.saveRole(new RoleCommand(
                null, name, roleKey, 0, "NONE", "NONE", 0, false), Set.of());
    }

    @PreAuthorize("@authz.require('system:role:edit')")
    @PutMapping
    @Operation(summary = "修改角色", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void update(@Valid @RequestBody RoleRequest request) {
        if (request.id() == null) {
            throw new IllegalArgumentException("角色 ID 不能为空");
        }
        roleService.saveRole(
                request.toCommand(),
                request.menuIds(),
                request.deptIds(),
                request.resourceGroupIds());
    }

    @PreAuthorize("@authz.require('system:role:remove')")
    @DeleteMapping("/delete")
    @Operation(summary = "删除角色", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void delete(@RequestParam long id) {
        roleService.delete(id);
    }

    @PreAuthorize("@authz.require('system:role:list')")
    @GetMapping("/all")
    @Operation(summary = "查询角色", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public List<Role> all() {
        return roleService.getAll();
    }
}
