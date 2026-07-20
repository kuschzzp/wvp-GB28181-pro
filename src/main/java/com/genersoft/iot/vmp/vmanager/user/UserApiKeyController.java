package com.genersoft.iot.vmp.vmanager.user;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyStatus;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyCreatedResponse;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyExpirationParser;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyQuery;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeySummary;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeUpdateRequest;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeOptions;
import com.genersoft.iot.vmp.auth.apikey.dto.CreateApiKeyCommand;
import com.genersoft.iot.vmp.auth.apikey.dto.CreateApiKeyRequest;
import com.genersoft.iot.vmp.auth.department.DepartmentScopeService;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IUserApiKeyService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@Tag(name = "用户 API Key 管理")
@RestController
@RequestMapping("/api/userApiKey")
public class UserApiKeyController {

    private final IUserApiKeyService apiKeyService;
    private final CurrentPrincipalService currentPrincipalService;
    private final DepartmentScopeService departmentScopeService;

    public UserApiKeyController(
            IUserApiKeyService apiKeyService,
            CurrentPrincipalService currentPrincipalService,
            DepartmentScopeService departmentScopeService) {
        this.apiKeyService = apiKeyService;
        this.currentPrincipalService = currentPrincipalService;
        this.departmentScopeService = departmentScopeService;
    }

    @PreAuthorize("@authz.require('system:apikey:add')")
    @PostMapping
    @Operation(summary = "创建 API Key（secret 仅返回本次）",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public ApiKeyCreatedResponse create(@Valid @RequestBody CreateApiKeyRequest request) {
        OwnerAccess access = ownerAccess(request.ownerId(), "system:apikey:add");
        return apiKeyService.create(access.ownerId(), request.toCommand(), access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:add')")
    @PostMapping("/add")
    @Operation(summary = "创建 API Key（旧版参数兼容）",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public ApiKeyCreatedResponse addLegacy(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String app,
            @RequestParam(required = false) String remark,
            @RequestParam(required = false) String expiresAt,
            @RequestParam(required = false, defaultValue = "false") boolean enable) {
        Instant expiration = expiresAt == null || expiresAt.isBlank()
                ? null
                : ApiKeyExpirationParser.parse(expiresAt);
        OwnerAccess access = ownerAccess(userId, "system:apikey:add");
        return apiKeyService.create(
                access.ownerId(),
                new CreateApiKeyCommand(app, remark, expiration, enable),
                access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:list')")
    @GetMapping("/userApiKeys")
    @Operation(summary = "分页查询 API Key 摘要",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public PageInfo<ApiKeySummary> page(
            @RequestParam int page,
            @RequestParam int count,
            @RequestParam(required = false) Long userId) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:list");
        return apiKeyService.pageOwnKeys(
                access.ownerId(), new ApiKeyQuery(page, count), access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:list')")
    @GetMapping("/scope-options")
    @Operation(summary = "查询目标用户可配置的 API Key 权限范围选项",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public ApiKeyScopeOptions scopeOptions(
            @RequestParam(required = false) Long userId) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:list");
        return apiKeyService.scopeOptions(access.ownerId(), access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:disable')")
    @PostMapping("/enable")
    public void enable(
            @RequestParam long id,
            @RequestParam(required = false) Long userId) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:disable");
        apiKeyService.changeStatus(
                access.ownerId(), id, ApiKeyStatus.ACTIVE, access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:disable')")
    @PostMapping("/disable")
    public void disable(
            @RequestParam long id,
            @RequestParam(required = false) Long userId) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:disable");
        apiKeyService.changeStatus(
                access.ownerId(), id, ApiKeyStatus.DISABLED, access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:rotate')")
    @PostMapping("/reset")
    @Operation(summary = "轮换 API Key（新 secret 仅返回本次）",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public ApiKeyCreatedResponse rotate(
            @RequestParam long id,
            @RequestParam(required = false) Long userId) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:rotate");
        return apiKeyService.rotate(access.ownerId(), id, access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:rotate')")
    @PostMapping("/remark")
    public void remark(
            @RequestParam long id,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String remark) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:rotate");
        apiKeyService.updateRemark(access.ownerId(), id, remark, access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:rotate')")
    @PutMapping("/{id}/scope")
    @Operation(summary = "修改 API Key 功能权限、视频资源组和 IP 白名单",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void updateScope(
            @PathVariable long id,
            @RequestParam(required = false) Long userId,
            @Valid @RequestBody ApiKeyScopeUpdateRequest request) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:rotate");
        apiKeyService.updateScope(
                access.ownerId(), id, request.toCommand(), access.scope());
    }

    @PreAuthorize("@authz.require('system:apikey:remove')")
    @DeleteMapping("/delete")
    public void delete(
            @RequestParam long id,
            @RequestParam(required = false) Long userId) {
        OwnerAccess access = ownerAccess(userId, "system:apikey:remove");
        apiKeyService.delete(access.ownerId(), id, access.scope());
    }

    private OwnerAccess ownerAccess(Long requestedOwnerId, String permissionCode) {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        if (principal.userId() == null || principal.userId() <= 0) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "API Key 管理主体必须绑定有效用户");
        }
        long ownerId = requestedOwnerId == null ? principal.userId() : requestedOwnerId;
        DepartmentScope scope = ownerId == principal.userId()
                ? DepartmentScope.self(principal.userId())
                : departmentScopeService.resolveDecisionScope(principal, permissionCode);
        return new OwnerAccess(ownerId, scope);
    }

    private record OwnerAccess(long ownerId, DepartmentScope scope) {
    }
}
