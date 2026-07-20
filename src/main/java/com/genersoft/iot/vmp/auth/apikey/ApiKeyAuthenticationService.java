package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;

@Service
public class ApiKeyAuthenticationService {

    private final ApiKeySecretHasher secretHasher;
    private final UserApiKeyMapper mapper;
    private final IUserService userService;
    private final PermissionRepository permissionRepository;
    private final ApiKeyScopeService scopeService;
    private final AuthorizationProperties properties;

    public ApiKeyAuthenticationService(
            ApiKeySecretHasher secretHasher,
            UserApiKeyMapper mapper,
            IUserService userService,
            PermissionRepository permissionRepository,
            ApiKeyScopeService scopeService,
            AuthorizationProperties properties) {
        this.secretHasher = secretHasher;
        this.mapper = mapper;
        this.userService = userService;
        this.permissionRepository = permissionRepository;
        this.scopeService = scopeService;
        this.properties = properties;
    }

    public AuthPrincipal authenticate(String rawKey, InetAddress clientAddress) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new InvalidAccessTokenException("API Key 不能为空");
        }
        UserApiKey key = mapper.selectByHash(secretHasher.hash(rawKey));
        if (key != null && !secretHasher.matches(rawKey, key.getKeyHash())) {
            throw new InvalidAccessTokenException("API Key 无效");
        }
        if (key == null) {
            key = authenticateLegacy(rawKey);
        }
        validateKey(key);
        validateClientAddress(key, clientAddress);

        User owner = userService.getUserById(key.getUserId());
        if (owner == null || owner.isDeleted() || owner.isLocked() || owner.getStatus() != 0) {
            throw new InvalidAccessTokenException("API Key 所属用户不可用");
        }
        if (key.getAuthzVersion() != owner.getTokenVersion()) {
            throw new InvalidAccessTokenException("API Key 授权版本已失效，请轮换 Key");
        }

        Set<Long> roleIds = Set.copyOf(permissionRepository.findEnabledRoleIds(owner.getId()));
        boolean systemOwner = permissionRepository.isSystemUser(owner.getId());
        AuthPrincipal ownerPrincipal = new AuthPrincipal(
                PrincipalType.USER,
                owner.getId(),
                owner.getId(),
                owner.getUsername(),
                owner.getDeptId(),
                roleIds,
                owner.getTokenVersion(),
                systemOwner,
                Set.of());
        Set<String> permissions = scopeService.resolvePermissions(key.getId(), ownerPrincipal);
        if (mapper.updateLastUsedAt(key.getId(), LocalDateTime.now(ZoneOffset.UTC)) != 1) {
            throw new InvalidAccessTokenException("API Key 已失效");
        }
        return new AuthPrincipal(
                PrincipalType.API_KEY,
                key.getId(),
                owner.getId(),
                owner.getUsername(),
                owner.getDeptId(),
                roleIds,
                owner.getTokenVersion(),
                false,
                permissions);
    }

    private UserApiKey authenticateLegacy(String rawKey) {
        if (!properties.legacyApiKeyEnabled()
                || !Instant.now().isBefore(Instant.parse(properties.legacyApiKeySunset()))) {
            throw new InvalidAccessTokenException("API Key 无效");
        }
        UserApiKey legacy = mapper.selectLegacyByRawKey(rawKey);
        if (legacy == null || legacy.getLegacyApiKey() == null
                || !MessageDigest.isEqual(
                rawKey.getBytes(StandardCharsets.UTF_8),
                legacy.getLegacyApiKey().getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidAccessTokenException("API Key 无效");
        }
        return legacy;
    }

    private void validateKey(UserApiKey key) {
        if (key == null || (key.getStatus() != ApiKeyStatus.ACTIVE
                && key.getStatus() != ApiKeyStatus.LEGACY)) {
            throw new InvalidAccessTokenException("API Key 已停用");
        }
        if (key.getStatus() == ApiKeyStatus.LEGACY
                && (!properties.legacyApiKeyEnabled()
                || !Instant.now().isBefore(Instant.parse(properties.legacyApiKeySunset())))) {
            throw new InvalidAccessTokenException("旧 API Key 已超过兼容期");
        }
        if (key.getExpiredAt() != null && key.getExpiredAt() > 0
                && key.getExpiredAt() <= System.currentTimeMillis()) {
            throw new InvalidAccessTokenException("API Key 已过期");
        }
    }

    private void validateClientAddress(UserApiKey key, InetAddress clientAddress) {
        try {
            if (!ApiKeyIpAllowlist.allows(key.getIpAllowlist(), clientAddress)) {
                throw new InvalidAccessTokenException("客户端地址不在 API Key 白名单内");
            }
        } catch (InvalidAccessTokenException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new InvalidAccessTokenException("API Key IP 白名单配置无效", exception);
        }
    }
}
