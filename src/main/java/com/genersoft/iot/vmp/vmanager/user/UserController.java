package com.genersoft.iot.vmp.vmanager.user;

import com.genersoft.iot.vmp.auth.annotation.PublicEndpoint;
import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.dto.AssignRolesRequest;
import com.genersoft.iot.vmp.auth.dto.CreateUserRequest;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;
import com.genersoft.iot.vmp.auth.dto.UserSummary;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.UserAuthenticationService;
import com.genersoft.iot.vmp.auth.user.SystemUserService;
import com.genersoft.iot.vmp.auth.user.dto.ChangeOwnPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.ResetPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
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
import java.util.ArrayList;
import java.util.Set;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/user")
public class UserController {

    private final IUserService userService;
    private final SystemUserService systemUserService;
    private final DepartmentScopeService departmentScopeService;
    private final UserAuthenticationService authenticationService;
    private final CurrentPrincipalService currentPrincipalService;
    private final AuthorizationProperties authorizationProperties;

    public UserController(
            IUserService userService,
            SystemUserService systemUserService,
            DepartmentScopeService departmentScopeService,
            UserAuthenticationService authenticationService,
            CurrentPrincipalService currentPrincipalService,
            AuthorizationProperties authorizationProperties) {
        this.userService = userService;
        this.systemUserService = systemUserService;
        this.departmentScopeService = departmentScopeService;
        this.authenticationService = authenticationService;
        this.currentPrincipalService = currentPrincipalService;
        this.authorizationProperties = authorizationProperties;
    }

    @PublicEndpoint(reason = "旧版登录兼容入口，受配置开关和 Sunset 期限约束")
    @PostMapping("/login")
    @Operation(summary = "旧版 MD5 登录（已弃用）")
    public ResponseEntity<WVPResult<LoginResponse>> legacyLogin(
            HttpServletRequest request,
            @RequestParam String username,
            @RequestParam String password) {
        LoginResponse response = authenticationService.legacyMd5Login(
                username, password, request.getRemoteAddr());
        return ResponseEntity.ok()
                .header("Deprecation", "true")
                .header("Sunset", authorizationProperties.legacyMd5LoginSunset())
                .header(JwtUtils.HEADER, response.accessToken())
                .body(WVPResult.success(response));
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping("/changePassword")
    @Operation(summary = "修改本人密码", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void changePassword(
            @RequestParam String oldPassword,
            @RequestParam @Size(min = 8, max = 128) String password) {
        systemUserService.changeOwnPassword(new ChangeOwnPasswordRequest(oldPassword, password));
    }

    @PreAuthorize("@authz.require('system:user:add')")
    @PostMapping
    @Operation(summary = "新增用户", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public long create(@Valid @RequestBody CreateUserRequest request) {
        return systemUserService.create(
                withDefaultDepartment(request), decisionScope("system:user:add"));
    }

    @PreAuthorize("@authz.require('system:user:add')")
    @PostMapping("/add")
    @Operation(summary = "新增用户（旧版兼容）", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public long addLegacy(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam long roleId) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return systemUserService.create(new CreateUserRequest(
                        username, password, username, principal.deptId(), Set.of(roleId)),
                departmentScopeService.resolveDecisionScope(principal, "system:user:add"));
    }

    @PreAuthorize("@authz.require('system:user:remove')")
    @DeleteMapping("/delete")
    @Operation(summary = "删除用户", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void delete(@RequestParam long id) {
        systemUserService.delete(id, decisionScope("system:user:remove"));
    }

    @PreAuthorize("@authz.require('system:user:remove')")
    @DeleteMapping("/{id}")
    public void deleteByPath(@PathVariable long id) {
        systemUserService.delete(id, decisionScope("system:user:remove"));
    }

    @PreAuthorize("@authz.require('system:user:list')")
    @GetMapping("/all")
    @Operation(summary = "查询全部用户", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public List<UserSummary> all() {
        DepartmentScope scope = decisionScope("system:user:list");
        PageInfo<com.genersoft.iot.vmp.auth.user.dto.UserSummary> firstPage =
                systemUserService.query(new UserQuery(1, 200, null, null, null), scope);
        List<UserSummary> result = new ArrayList<>();
        firstPage.getList().stream().map(this::toCompatibilitySummary).forEach(result::add);
        for (int page = 2; page <= firstPage.getPages(); page++) {
            systemUserService.query(new UserQuery(page, 200, null, null, null), scope)
                    .getList().stream()
                    .map(this::toCompatibilitySummary)
                    .forEach(result::add);
        }
        return List.copyOf(result);
    }

    @PreAuthorize("@authz.require('system:user:list')")
    @GetMapping("/users")
    @Operation(summary = "分页查询用户", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public PageInfo<UserSummary> users(@RequestParam int page, @RequestParam int count) {
        return systemUserService.query(
                        new UserQuery(page, count, null, null, null),
                        decisionScope("system:user:list"))
                .convert(this::toCompatibilitySummary);
    }

    @PreAuthorize("@authz.require('system:role:assign')")
    @PutMapping("/{userId}/roles")
    @Operation(summary = "分配用户角色", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void assignRoles(
            @PathVariable long userId,
            @Valid @RequestBody AssignRolesRequest request) {
        systemUserService.assignRoles(
                userId, request.roleIds(), decisionScope("system:role:assign"));
    }

    @PreAuthorize("@authz.require('system:user:edit')")
    @PostMapping("/changePushKey")
    @Operation(summary = "修改推流密钥", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void changePushKey(@RequestParam long userId, @RequestParam String pushKey) {
        systemUserService.changePushKey(
                userId, pushKey, decisionScope("system:user:edit"));
    }

    @PreAuthorize("@authz.require('system:user:resetPwd')")
    @PostMapping("/changePasswordForAdmin")
    @Operation(summary = "管理员重置用户密码", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void resetPassword(
            @RequestParam long userId,
            @RequestParam @Size(min = 8, max = 128) String password) {
        systemUserService.resetPassword(
                userId,
                new ResetPasswordRequest(password),
                decisionScope("system:user:resetPwd"));
    }

    @PreAuthorize("isAuthenticated()")
    @RequestMapping("/userInfo")
    @Operation(summary = "当前用户摘要", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public UserSummary getUserInfo() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return UserSummary.from(userService.getUserById(principal.userId()));
    }

    private DepartmentScope decisionScope(String permissionCode) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return departmentScopeService.resolveDecisionScope(principal, permissionCode);
    }

    private CreateUserRequest withDefaultDepartment(CreateUserRequest request) {
        if (request.deptId() != null) {
            return request;
        }
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        return new CreateUserRequest(
                request.username(),
                request.password(),
                request.nickname(),
                principal.deptId(),
                request.roleIds());
    }

    private UserSummary toCompatibilitySummary(
            com.genersoft.iot.vmp.auth.user.dto.UserSummary user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDeptId(),
                user.getStatus(),
                user.getLocked() != 0,
                user.getRoleIds(),
                user.getLastLoginAt());
    }
}
