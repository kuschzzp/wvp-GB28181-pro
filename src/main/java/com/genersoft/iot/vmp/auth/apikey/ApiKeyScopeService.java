package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeCommand;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyPermissionOption;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeOptions;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeSnapshot;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.authorization.video.UserVideoScopeResolver;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.storager.dao.ApiKeyPermissionMapper;
import com.genersoft.iot.vmp.storager.dao.ApiKeyResourceGroupMapper;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ApiKeyScopeService {

    private final ApiKeyPermissionMapper permissionMapper;
    private final ApiKeyResourceGroupMapper resourceGroupMapper;
    private final UserApiKeyMapper userApiKeyMapper;
    private final PermissionService permissionService;
    private final PermissionRepository permissionRepository;
    private final UserVideoScopeResolver ownerScopeResolver;
    private final ResourceResolverRegistry resolverRegistry;
    private final MenuMapper menuMapper;
    private final ResourceGroupService resourceGroupService;

    public ApiKeyScopeService(
            ApiKeyPermissionMapper permissionMapper,
            ApiKeyResourceGroupMapper resourceGroupMapper,
            UserApiKeyMapper userApiKeyMapper,
            PermissionService permissionService,
            PermissionRepository permissionRepository,
            UserVideoScopeResolver ownerScopeResolver,
            ResourceResolverRegistry resolverRegistry,
            MenuMapper menuMapper,
            ResourceGroupService resourceGroupService) {
        this.permissionMapper = permissionMapper;
        this.resourceGroupMapper = resourceGroupMapper;
        this.userApiKeyMapper = userApiKeyMapper;
        this.permissionService = permissionService;
        this.permissionRepository = permissionRepository;
        this.ownerScopeResolver = ownerScopeResolver;
        this.resolverRegistry = resolverRegistry;
        this.menuMapper = menuMapper;
        this.resourceGroupService = resourceGroupService;
    }

    public Set<String> resolvePermissions(long apiKeyId, AuthPrincipal owner) {
        requireApiKeyId(apiKeyId);
        long ownerId = requireOwnerId(owner);
        List<String> keyCodes = permissionMapper.findEnabledPermissionCodes(apiKeyId);
        if (keyCodes == null || keyCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> ownerCodes = permissionService.resolvePermissions(
                ownerId, owner.tokenVersion());
        TreeSet<String> intersection = new TreeSet<>();
        for (String code : keyCodes) {
            if (code != null && !code.isBlank() && ownerCodes.contains(code.trim())) {
                intersection.add(code.trim());
            }
        }
        return Set.copyOf(intersection);
    }

    public VideoScope resolveVideoScope(
            long apiKeyId,
            String permissionCode,
            AuthPrincipal owner) {
        requireApiKeyId(apiKeyId);
        String permission = requirePermissionCode(permissionCode);
        long ownerId = requireOwnerId(owner);
        if (!resolvePermissions(apiKeyId, owner).contains(permission)) {
            return VideoScope.none();
        }

        boolean systemOwner = permissionRepository.isSystemUser(ownerId);
        VideoScope ownerScope = ownerScopeResolver.resolve(
                ownerId, permission, systemOwner);
        if (ownerScope.isNone()) {
            return VideoScope.none();
        }
        List<VideoScopeResourceRow> rows = resourceGroupMapper.findEnabledResources(apiKeyId);
        return intersectRows(rows, ownerScope);
    }

    @Transactional
    public void replaceScope(long apiKeyId, User owner, ApiKeyScopeCommand command) {
        requireApiKeyId(apiKeyId);
        Objects.requireNonNull(owner, "owner must not be null");
        if (owner.getId() <= 0) {
            throw new IllegalArgumentException("API Key 所属用户不存在");
        }
        ValidatedScope validated = validate(owner, command);
        permissionMapper.deleteByApiKeyId(apiKeyId);
        resourceGroupMapper.deleteByApiKeyId(apiKeyId);
        for (Long menuId : validated.menuIds().stream().sorted().toList()) {
            if (permissionMapper.insert(apiKeyId, menuId) != 1) {
                throw new IllegalStateException("API Key 功能权限保存失败");
            }
        }
        for (Long groupId : validated.groupIds().stream().sorted().toList()) {
            if (resourceGroupMapper.insert(apiKeyId, groupId) != 1) {
                throw new IllegalStateException("API Key 视频资源范围保存失败");
            }
        }
        if (userApiKeyMapper.updateIpAllowlist(apiKeyId, validated.encodedIpAllowlist()) != 1) {
            throw new IllegalStateException("API Key IP 白名单保存失败");
        }
    }

    public void validateStoredScope(UserApiKey key, User owner) {
        Objects.requireNonNull(key, "API Key must not be null");
        validate(owner, new ApiKeyScopeCommand(
                normalizeIds(permissionMapper.findMenuIds(key.getId()), "permissionMenuIds"),
                normalizeIds(resourceGroupMapper.findGroupIds(key.getId()), "resourceGroupIds"),
                ApiKeyIpAllowlist.decode(key.getIpAllowlist())));
    }

    public ApiKeyScopeSnapshot snapshot(UserApiKey key) {
        Objects.requireNonNull(key, "API Key must not be null");
        return new ApiKeyScopeSnapshot(
                normalizeIds(permissionMapper.findMenuIds(key.getId()), "permissionMenuIds"),
                normalizeIds(resourceGroupMapper.findGroupIds(key.getId()), "resourceGroupIds"),
                ApiKeyIpAllowlist.decode(key.getIpAllowlist()));
    }

    public ApiKeyScopeOptions options(User owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        if (owner.getId() <= 0) {
            throw new IllegalArgumentException("API Key 所属用户不存在");
        }
        Set<String> ownerPermissions = permissionService.resolvePermissions(
                owner.getId(), owner.getTokenVersion());
        List<Menu> allMenus = menuMapper.findAll();
        if (allMenus == null) {
            throw new IllegalStateException("API Key 权限选项查询失败");
        }
        HashMap<Long, Menu> menuById = new HashMap<>();
        for (Menu menu : allMenus) {
            if (menu != null && menu.getId() > 0) {
                menuById.put(menu.getId(), menu);
            }
        }
        HashSet<Long> includedIds = new HashSet<>();
        for (Menu menu : allMenus) {
            if (isSelectableForOwner(menu, ownerPermissions)) {
                includeMenuPath(menu, menuById, includedIds);
            }
        }
        List<ApiKeyPermissionOption> permissionMenus = allMenus.stream()
                .filter(Objects::nonNull)
                .filter(menu -> includedIds.contains(menu.getId()))
                .map(menu -> new ApiKeyPermissionOption(
                        menu.getId(),
                        menu.getParentId(),
                        menu.getMenuName(),
                        menu.getMenuType(),
                        menu.getPerms(),
                        menu.getOrderNum(),
                        isSelectableForOwner(menu, ownerPermissions)))
                .toList();
        List<ResourceGroupTreeNode> resourceGroups = resourceGroupService.tree().stream()
                .map(node -> normalizeResourceGroupPath(node, false))
                .toList();
        return new ApiKeyScopeOptions(permissionMenus, resourceGroups);
    }

    private ValidatedScope validate(User owner, ApiKeyScopeCommand command) {
        Objects.requireNonNull(owner, "owner must not be null");
        Objects.requireNonNull(command, "API Key scope must not be null");
        Set<Long> menuIds = normalizeIds(command.permissionMenuIds(), "permissionMenuIds");
        Set<Long> groupIds = normalizeIds(command.resourceGroupIds(), "resourceGroupIds");
        List<String> ipAllowlist = ApiKeyIpAllowlist.normalize(command.ipAllowlist());

        List<String> requestedCodes = List.of();
        if (!menuIds.isEmpty()) {
            if (permissionMapper.countEnabledPermissionMenus(menuIds) != menuIds.size()) {
                throw new IllegalArgumentException("API Key 只能绑定启用且包含权限编码的菜单节点");
            }
            requestedCodes = normalizeCodes(
                    permissionMapper.findEnabledPermissionCodesByMenuIds(menuIds));
            if (requestedCodes.size() != menuIds.size()) {
                throw new IllegalStateException("API Key 权限菜单查询结果不完整");
            }
        }

        Set<String> ownerPermissions = permissionService.resolvePermissions(
                owner.getId(), owner.getTokenVersion());
        if (!ownerPermissions.containsAll(requestedCodes)) {
            throw new AccessDeniedException("API Key 功能权限不能超过所属用户当前权限");
        }

        if (!groupIds.isEmpty()) {
            if (resourceGroupMapper.countEnabledResourceGroups(groupIds) != groupIds.size()) {
                throw new IllegalArgumentException("API Key 只能绑定存在且整条路径启用的视频资源组");
            }
            List<VideoScopeResourceRow> rows =
                    resourceGroupMapper.findEnabledResourcesByGroupIds(groupIds);
            validateResourcesWithinOwner(owner, requestedCodes, rows);
        }
        return new ValidatedScope(
                menuIds,
                groupIds,
                ApiKeyIpAllowlist.encode(ipAllowlist));
    }

    private void validateResourcesWithinOwner(
            User owner,
            List<String> requestedCodes,
            List<VideoScopeResourceRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<String> videoPermissions = requestedCodes.stream()
                .filter(code -> code.startsWith("video:"))
                .toList();
        if (videoPermissions.isEmpty()) {
            throw new AccessDeniedException("绑定视频资源组时至少需要一个视频功能权限");
        }
        boolean systemOwner = permissionRepository.isSystemUser(owner.getId());
        List<VideoScope> ownerScopes = new ArrayList<>();
        for (String permission : videoPermissions) {
            ownerScopes.add(ownerScopeResolver.resolve(
                    owner.getId(), permission, systemOwner));
        }
        for (VideoScopeResourceRow row : rows) {
            VideoResourceRef resource = toResource(row);
            if (ownerScopes.stream().noneMatch(scope -> allows(scope, resource))) {
                throw new AccessDeniedException("API Key 视频资源范围不能超过所属用户当前范围");
            }
        }
    }

    private VideoScope intersectRows(List<VideoScopeResourceRow> rows, VideoScope ownerScope) {
        if (rows == null || rows.isEmpty()) {
            return VideoScope.none();
        }
        LinkedHashSet<VideoResourceRef> sourceResources = new LinkedHashSet<>();
        LinkedHashSet<Long> explicitChannelIds = new LinkedHashSet<>();
        for (VideoScopeResourceRow row : rows) {
            VideoResourceRef resource = toResource(row);
            if (!allows(ownerScope, resource)) {
                continue;
            }
            if (resource.type() == VideoResourceType.CHANNEL) {
                explicitChannelIds.add(resource.id());
            } else {
                sourceResources.add(resource);
            }
        }
        return new VideoScope(false, Set.of(), sourceResources, explicitChannelIds);
    }

    private boolean allows(VideoScope scope, VideoResourceRef resource) {
        if (scope.all()) {
            return true;
        }
        Set<VideoResourceRef> candidates =
                resolverRegistry.resolveAuthorizationCandidates(resource);
        for (VideoResourceRef candidate : candidates) {
            if (candidate.type() == VideoResourceType.CHANNEL) {
                if (scope.explicitChannelIds().contains(candidate.id())) {
                    return true;
                }
            } else if (scope.sourceResources().contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private VideoResourceRef toResource(VideoScopeResourceRow row) {
        if (row == null || row.resourceType() == null || row.resourceId() <= 0
                || !row.resourceType().isBindable()) {
            throw new IllegalStateException("API Key 资源组包含非法视频资源");
        }
        return new VideoResourceRef(row.resourceType(), row.resourceId());
    }

    private List<String> normalizeCodes(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalStateException("API Key 权限菜单包含空权限编码");
            }
            normalized.add(value.trim());
        }
        return List.copyOf(normalized);
    }

    private boolean isSelectableForOwner(Menu menu, Set<String> ownerPermissions) {
        return menu != null
                && menu.getStatus() == 0
                && menu.getPerms() != null
                && !menu.getPerms().isBlank()
                && ownerPermissions.contains(menu.getPerms().trim());
    }

    private void includeMenuPath(
            Menu menu,
            HashMap<Long, Menu> menuById,
            Set<Long> includedIds) {
        HashSet<Long> visited = new HashSet<>();
        Menu current = menu;
        while (current != null && current.getId() > 0) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException("API Key 权限菜单树存在循环");
            }
            includedIds.add(current.getId());
            if (current.getParentId() == 0) {
                return;
            }
            current = menuById.get(current.getParentId());
        }
    }

    private ResourceGroupTreeNode normalizeResourceGroupPath(
            ResourceGroupTreeNode node,
            boolean ancestorDisabled) {
        boolean disabled = ancestorDisabled || node.status() != 0;
        return new ResourceGroupTreeNode(
                node.id(),
                node.parentId(),
                node.groupCode(),
                node.groupName(),
                node.orderNum(),
                disabled ? 1 : 0,
                node.description(),
                node.children().stream()
                        .map(child -> normalizeResourceGroupPath(child, disabled))
                        .toList());
    }

    private Set<Long> normalizeIds(Collection<Long> values, String fieldName) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        TreeSet<Long> normalized = new TreeSet<>();
        for (Long value : values) {
            if (value == null || value <= 0) {
                throw new IllegalArgumentException(fieldName + " must contain only positive IDs");
            }
            normalized.add(value);
        }
        return Set.copyOf(normalized);
    }

    private long requireOwnerId(AuthPrincipal owner) {
        Objects.requireNonNull(owner, "owner must not be null");
        if (owner.userId() == null || owner.userId() <= 0) {
            throw new IllegalArgumentException("API Key owner must bind a valid user");
        }
        return owner.userId();
    }

    private void requireApiKeyId(long apiKeyId) {
        if (apiKeyId <= 0) {
            throw new IllegalArgumentException("apiKeyId must be positive");
        }
    }

    private String requirePermissionCode(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            throw new IllegalArgumentException("permissionCode must not be blank");
        }
        return permissionCode.trim();
    }

    private record ValidatedScope(
            Set<Long> menuIds,
            Set<Long> groupIds,
            String encodedIpAllowlist) {
    }
}
