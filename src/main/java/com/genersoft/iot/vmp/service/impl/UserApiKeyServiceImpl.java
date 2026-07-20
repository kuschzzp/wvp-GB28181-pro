package com.genersoft.iot.vmp.service.impl;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyScopeService;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyStatus;
import com.genersoft.iot.vmp.auth.apikey.GeneratedApiKey;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyCreatedResponse;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyQuery;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeySummary;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeCommand;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeOptions;
import com.genersoft.iot.vmp.auth.apikey.dto.CreateApiKeyCommand;
import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.service.IUserApiKeyService;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;
import com.genersoft.iot.vmp.utils.DateUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class UserApiKeyServiceImpl implements IUserApiKeyService {

    private static final String MANAGE_OTHERS = "system:apikey:manageOthers";

    private final UserApiKeyMapper mapper;
    private final IUserService userService;
    private final ApiKeySecretHasher secretHasher;
    private final CurrentPrincipalService currentPrincipalService;
    private final DepartmentCommandPolicy commandPolicy;
    private final ApiKeyScopeService scopeService;

    public UserApiKeyServiceImpl(
            UserApiKeyMapper mapper,
            IUserService userService,
            ApiKeySecretHasher secretHasher,
            CurrentPrincipalService currentPrincipalService,
            DepartmentCommandPolicy commandPolicy,
            ApiKeyScopeService scopeService) {
        this.mapper = mapper;
        this.userService = userService;
        this.secretHasher = secretHasher;
        this.currentPrincipalService = currentPrincipalService;
        this.commandPolicy = commandPolicy;
        this.scopeService = scopeService;
    }

    @Override
    @Transactional
    public ApiKeyCreatedResponse create(
            long ownerId,
            CreateApiKeyCommand command,
            DepartmentScope scope) {
        authorizeOwner(ownerId, scope);
        validateCommand(command);
        User owner = requireActiveOwner(ownerId);
        validateExpiration(command.expiresAt());
        GeneratedApiKey generated = secretHasher.generate();
        UserApiKey key = new UserApiKey();
        key.setUserId(ownerId);
        key.setApp(command.app());
        key.setRemark(command.remark());
        key.setKeyHash(generated.hash());
        key.setKeyPrefix(generated.prefix());
        key.setKeyLast4(generated.last4());
        key.setExpiredAt(command.expiresAt() == null ? null : command.expiresAt().toEpochMilli());
        key.setStatus(command.enabled() ? ApiKeyStatus.ACTIVE : ApiKeyStatus.DISABLED);
        key.setAuthzVersion(owner.getTokenVersion());
        key.setCreateTime(DateUtil.getNow());
        key.setUpdateTime(DateUtil.getNow());
        if (mapper.add(key) <= 0) {
            throw new IllegalStateException("API Key 创建失败");
        }
        scopeService.replaceScope(key.getId(), owner, command.scope());
        return createdResponse(key, generated.raw());
    }

    @Override
    @Transactional
    public ApiKeyCreatedResponse rotate(
            long ownerId,
            long keyId,
            DepartmentScope scope) {
        UserApiKey key = authorizeKey(ownerId, keyId, scope);
        User owner = requireActiveOwner(ownerId);
        if (key.getExpiredAt() != null && key.getExpiredAt() > 0
                && key.getExpiredAt() <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("API Key 已过期");
        }
        if (key.getExpiredAt() != null && key.getExpiredAt() <= 0) {
            key.setExpiredAt(null);
        }
        scopeService.validateStoredScope(key, owner);
        GeneratedApiKey generated = secretHasher.generate();
        key.setKeyHash(generated.hash());
        key.setKeyPrefix(generated.prefix());
        key.setKeyLast4(generated.last4());
        key.setLegacyApiKey(null);
        key.setStatus(ApiKeyStatus.ACTIVE);
        key.setAuthzVersion(owner.getTokenVersion());
        key.setUpdateTime(DateUtil.getNow());
        if (mapper.rotate(key) <= 0) {
            throw new IllegalStateException("API Key 轮换失败");
        }
        return createdResponse(key, generated.raw());
    }

    @Override
    public PageInfo<ApiKeySummary> pageOwnKeys(
            long ownerId,
            ApiKeyQuery query,
            DepartmentScope scope) {
        authorizeOwner(ownerId, scope);
        PageHelper.startPage(query.page(), query.count());
        List<UserApiKey> keys = mapper.findByOwnerId(ownerId);
        return new PageInfo<>(keys).convert(
                key -> ApiKeySummary.from(key, scopeService.snapshot(key)));
    }

    @Override
    public ApiKeyScopeOptions scopeOptions(long ownerId, DepartmentScope scope) {
        authorizeOwner(ownerId, scope);
        return scopeService.options(requireActiveOwner(ownerId));
    }

    @Override
    @Transactional
    public void changeStatus(
            long ownerId,
            long keyId,
            ApiKeyStatus status,
            DepartmentScope scope) {
        if (status != ApiKeyStatus.ACTIVE && status != ApiKeyStatus.DISABLED) {
            throw new IllegalArgumentException("API Key 状态不合法");
        }
        UserApiKey key = authorizeKey(ownerId, keyId, scope);
        if (status == ApiKeyStatus.ACTIVE) {
            validateActivation(ownerId, key);
        }
        if (mapper.updateStatus(ownerId, keyId, status, DateUtil.getNow()) <= 0) {
            throw new IllegalStateException("API Key 状态更新失败");
        }
    }

    @Override
    @Transactional
    public void updateRemark(
            long ownerId,
            long keyId,
            String remark,
            DepartmentScope scope) {
        authorizeKey(ownerId, keyId, scope);
        validateOptionalText(remark, "备注", 255);
        if (mapper.updateRemark(ownerId, keyId, remark, DateUtil.getNow()) <= 0) {
            throw new IllegalStateException("API Key 备注更新失败");
        }
    }

    @Override
    @Transactional
    public void updateScope(
            long ownerId,
            long keyId,
            ApiKeyScopeCommand command,
            DepartmentScope scope) {
        UserApiKey key = authorizeKey(ownerId, keyId, scope);
        if (key.getStatus() == ApiKeyStatus.REVOKED) {
            throw new IllegalArgumentException("已撤销的 API Key 不能修改范围");
        }
        User owner = requireActiveOwner(ownerId);
        scopeService.replaceScope(keyId, owner, command);
    }

    @Override
    @Transactional
    public void delete(long ownerId, long keyId, DepartmentScope scope) {
        authorizeKey(ownerId, keyId, scope);
        if (mapper.delete(ownerId, keyId) <= 0) {
            throw new IllegalStateException("API Key 删除失败");
        }
    }

    private UserApiKey authorizeKey(long ownerId, long keyId, DepartmentScope scope) {
        authorizeOwner(ownerId, scope);
        UserApiKey key = mapper.selectById(keyId);
        if (key == null || key.getUserId() != ownerId) {
            throw new IllegalArgumentException("API Key 不存在");
        }
        return key;
    }

    private void authorizeOwner(long ownerId, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        if (principal.userId() == null || principal.userId() <= 0) {
            throw new AccessDeniedException("无权管理其他用户的 API Key");
        }
        if (principal.userId() == ownerId) {
            return;
        }
        commandPolicy.requireUserInScope(ownerId, scope);
        if (!principal.permissions().contains(MANAGE_OTHERS)) {
            throw new AccessDeniedException("无权管理其他用户的 API Key");
        }
    }

    private User requireActiveOwner(long ownerId) {
        User owner = userService.getUserById(ownerId);
        if (owner == null || owner.isDeleted() || owner.isLocked() || owner.getStatus() != 0) {
            throw new IllegalArgumentException("所属用户不存在或不可用");
        }
        return owner;
    }

    private void validateExpiration(Instant expiresAt) {
        if (expiresAt != null && !expiresAt.isAfter(Instant.now())) {
            throw new IllegalArgumentException("过期时间必须晚于当前时间");
        }
    }

    private void validateCommand(CreateApiKeyCommand command) {
        Objects.requireNonNull(command, "API Key 创建参数不能为空");
        validateOptionalText(command.app(), "应用标识", 255);
        validateOptionalText(command.remark(), "备注", 255);
    }

    private void validateOptionalText(String value, String fieldName, int maxLength) {
        if (value != null && value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength + " 个字符");
        }
    }

    private void validateActivation(long ownerId, UserApiKey key) {
        if (key.getKeyHash() == null || key.getKeyHash().isBlank()) {
            throw new IllegalArgumentException("旧版 API Key 必须先轮换后才能启用");
        }
        User owner = requireActiveOwner(ownerId);
        if (key.getAuthzVersion() != owner.getTokenVersion()) {
            throw new IllegalArgumentException("API Key 授权版本已失效，请先轮换");
        }
        if (key.getExpiredAt() != null && key.getExpiredAt() > 0
                && key.getExpiredAt() <= System.currentTimeMillis()) {
            throw new IllegalArgumentException("API Key 已过期");
        }
        scopeService.validateStoredScope(key, owner);
    }

    private ApiKeyCreatedResponse createdResponse(UserApiKey key, String rawSecret) {
        return new ApiKeyCreatedResponse(
                key.getId(),
                rawSecret,
                key.getKeyPrefix(),
                key.getKeyLast4(),
                key.getExpiredAt() == null ? null : Instant.ofEpochMilli(key.getExpiredAt()));
    }
}
