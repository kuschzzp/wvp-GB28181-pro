package com.genersoft.iot.vmp.authorization.resourcegroup.impl;

import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupItemMapper;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupMapper;
import com.genersoft.iot.vmp.authorization.resourcegroup.ResourceGroupService;
import com.genersoft.iot.vmp.authorization.resourcegroup.RoleResourceGroupMapper;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.CreateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.UpdateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroup;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroupClosurePath;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroupItem;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.RoleVideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class ResourceGroupServiceImpl implements ResourceGroupService {

    private static final String ROOT_CODE = "ROOT_VIDEO";

    private final ResourceGroupMapper groupMapper;
    private final ResourceGroupItemMapper itemMapper;
    private final RoleResourceGroupMapper roleGroupMapper;
    private final ResourceResolverRegistry resolverRegistry;
    private final TokenVersionService tokenVersionService;

    public ResourceGroupServiceImpl(
            ResourceGroupMapper groupMapper,
            ResourceGroupItemMapper itemMapper,
            RoleResourceGroupMapper roleGroupMapper,
            ResourceResolverRegistry resolverRegistry,
            TokenVersionService tokenVersionService) {
        this.groupMapper = groupMapper;
        this.itemMapper = itemMapper;
        this.roleGroupMapper = roleGroupMapper;
        this.resolverRegistry = resolverRegistry;
        this.tokenVersionService = tokenVersionService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceGroupTreeNode> tree() {
        List<ResourceGroup> rows = groupMapper.findAll();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Map<Long, TreeBuilder> nodes = new LinkedHashMap<>();
        for (ResourceGroup row : rows) {
            if (row != null && row.getId() != null) {
                nodes.put(row.getId(), new TreeBuilder(row));
            }
        }
        List<TreeBuilder> roots = new ArrayList<>();
        for (TreeBuilder node : nodes.values()) {
            TreeBuilder parent = nodes.get(node.group.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        roots.sort(TREE_ORDER);
        return roots.stream().map(TreeBuilder::toNode).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResourceGroupItem> findItems(long groupId) {
        if (groupId <= 0 || groupMapper.findById(groupId) == null) {
            throw new IllegalArgumentException("视频资源组不存在");
        }
        List<ResourceGroupItem> items = itemMapper.findByGroupId(groupId);
        return items == null ? List.of() : List.copyOf(items);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findRoleGroupIds(long roleId) {
        if (roleId <= 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        List<Long> groupIds = roleGroupMapper.findGroupIdsByRoleId(roleId);
        return groupIds == null ? Set.of() : normalizeGroupIds(groupIds);
    }

    @Override
    @Transactional
    public long create(CreateResourceGroupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.parentId() <= 0) {
            throw new IllegalArgumentException("上级视频资源组不存在");
        }
        Long operatorId = normalizeOperatorId(command.operatorId());
        String groupCode = normalizeRequired(command.groupCode(), "资源组编码", 64);
        String groupName = normalizeRequired(command.groupName(), "资源组名称", 64);
        String description = normalizeOptional(command.description(), "资源组描述", 500);
        requireStatus(command.status());
        if (ROOT_CODE.equals(groupCode)) {
            throw new AccessDeniedException("ROOT_VIDEO 是系统保留资源组");
        }

        ResourceGroup parent = requireEnabledGroupForUpdate(command.parentId());
        List<ResourceGroupClosurePath> parentPaths =
                groupMapper.findAncestorPathsForUpdate(parent.getId());
        requireCompletePath(parent.getId(), parentPaths, true);
        if (groupMapper.countByCode(groupCode) > 0) {
            throw new IllegalArgumentException("资源组编码已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        ResourceGroup group = new ResourceGroup();
        group.setParentId(parent.getId());
        group.setGroupCode(groupCode);
        group.setGroupName(groupName);
        group.setOrderNum(command.orderNum());
        group.setStatus(command.status());
        group.setDescription(description);
        group.setCreateBy(operatorId);
        group.setCreateTime(now);
        group.setUpdateBy(operatorId);
        group.setUpdateTime(now);

        if (groupMapper.insert(group) != 1 || group.getId() == null) {
            throw new IllegalStateException("视频资源组创建失败");
        }
        int ancestorRows = groupMapper.insertAncestorPaths(parent.getId(), group.getId());
        if (ancestorRows != parentPaths.size() || groupMapper.insertSelfPath(group.getId()) != 1) {
            throw new IllegalStateException("视频资源组闭包关系创建失败");
        }
        return group.getId();
    }

    @Override
    @Transactional
    public void move(long groupId, long parentId) {
        move(groupId, parentId, null);
    }

    @Override
    @Transactional
    public void move(long groupId, long parentId, Long operatorId) {
        Set<Long> affectedUserIds = moveInternal(
                groupId, parentId, normalizeOperatorId(operatorId));
        bumpUsers(affectedUserIds, "RESOURCE_GROUP_MOVED");
    }

    @Override
    @Transactional
    public void update(UpdateResourceGroupCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (command.id() <= 0) {
            throw new IllegalArgumentException("视频资源组不存在");
        }
        Long operatorId = normalizeOperatorId(command.operatorId());
        String groupCode = normalizeRequired(command.groupCode(), "资源组编码", 64);
        String groupName = normalizeRequired(command.groupName(), "资源组名称", 64);
        String description = normalizeOptional(command.description(), "资源组描述", 500);
        requireStatus(command.status());

        ResourceGroup current = requireGroupForUpdate(command.id());
        if (current.isRoot()) {
            if (command.parentId() != 0
                    || !ROOT_CODE.equals(groupCode)
                    || command.status() != 0) {
                throw new AccessDeniedException("ROOT_VIDEO 的父级、编码和状态不能修改");
            }
        } else if (command.parentId() <= 0 || command.id() == command.parentId()) {
            throw new IllegalArgumentException("视频资源组或上级资源组非法");
        }
        if (groupMapper.countByCodeExcludingId(groupCode, command.id()) > 0) {
            throw new IllegalArgumentException("资源组编码已存在");
        }

        Set<Long> affectedUserIds = new LinkedHashSet<>();
        boolean statusChanged = current.getStatus() != command.status();
        if (statusChanged) {
            addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(command.id()));
        }
        if (!current.isRoot() && current.getParentId() != command.parentId()) {
            affectedUserIds.addAll(moveInternal(command.id(), command.parentId(), operatorId));
        }
        if (groupMapper.updateDetails(
                command.id(),
                groupCode,
                groupName,
                command.orderNum(),
                command.status(),
                description,
                operatorId,
                LocalDateTime.now()) != 1) {
            throw new IllegalStateException("视频资源组更新失败");
        }
        if (statusChanged) {
            addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(command.id()));
        }
        bumpUsers(affectedUserIds, "RESOURCE_GROUP_UPDATED");
    }

    @Override
    @Transactional
    public void delete(long groupId, Long migrationTargetGroupId, Long operatorId) {
        if (groupId <= 0) {
            throw new IllegalArgumentException("视频资源组不存在");
        }
        Long normalizedOperatorId = normalizeOperatorId(operatorId);
        ResourceGroup source = requireGroupForUpdate(groupId);
        if (source.isRoot()) {
            throw new AccessDeniedException("ROOT_VIDEO 不能删除");
        }
        if (groupMapper.countDirectChildren(groupId) > 0) {
            throw new IllegalStateException("资源组存在子节点，请先移动子节点");
        }

        int itemCount = itemMapper.countByGroupId(groupId);
        int roleCount = roleGroupMapper.countByGroupId(groupId);
        Set<Long> affectedUserIds = new LinkedHashSet<>();
        addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(groupId));
        if (itemCount > 0 || roleCount > 0) {
            if (migrationTargetGroupId == null
                    || migrationTargetGroupId <= 0
                    || migrationTargetGroupId == groupId) {
                throw new IllegalStateException("删除资源组前必须指定有效迁移目标");
            }
            ResourceGroup target = requireEnabledGroupForUpdate(migrationTargetGroupId);
            requireCompletePath(
                    target.getId(),
                    groupMapper.findAncestorPathsForUpdate(target.getId()),
                    true);
            addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(target.getId()));
            itemMapper.migrateToGroup(
                    groupId, target.getId(), normalizedOperatorId, LocalDateTime.now());
            roleGroupMapper.migrateToGroup(groupId, target.getId());
        }
        if (groupMapper.deleteById(groupId) != 1) {
            throw new IllegalStateException("视频资源组删除失败");
        }
        if (migrationTargetGroupId != null && migrationTargetGroupId > 0) {
            addAll(affectedUserIds,
                    roleGroupMapper.findUserIdsRelatedToGroup(migrationTargetGroupId));
        }
        bumpUsers(affectedUserIds, "RESOURCE_GROUP_DELETED");
    }

    @Override
    @Transactional
    public void replaceItems(long groupId, Set<VideoResourceRef> items) {
        replaceItems(groupId, items, null);
    }

    @Override
    @Transactional
    public void replaceItems(
            long groupId,
            Set<VideoResourceRef> items,
            Long operatorId) {
        Objects.requireNonNull(items, "items must not be null");
        Long normalizedOperatorId = normalizeOperatorId(operatorId);
        ResourceGroup group = requireEnabledGroupForUpdate(groupId);
        requireCompletePath(
                group.getId(),
                groupMapper.findAncestorPathsForUpdate(group.getId()),
                true);

        LinkedHashSet<VideoResourceRef> validatedItems = new LinkedHashSet<>();
        for (VideoResourceRef item : items) {
            if (item == null) {
                throw new IllegalArgumentException("视频资源项不能为空");
            }
            if (!item.type().isBindable()) {
                throw new IllegalArgumentException("该视频资源类型不能直接加入资源组: " + item.type());
            }
            VideoResourceRef existing = resolverRegistry.requireExisting(item);
            if (!item.equals(existing)) {
                throw new IllegalStateException("资源解析器返回了不一致的内部资源");
            }
            validatedItems.add(existing);
        }

        Set<Long> affectedUserIds = new LinkedHashSet<>();
        addAll(affectedUserIds, roleGroupMapper.findUserIdsCoveringGroup(groupId));
        itemMapper.deleteByGroupId(groupId);
        LocalDateTime now = LocalDateTime.now();
        for (VideoResourceRef item : validatedItems) {
            if (itemMapper.insert(new ResourceGroupItem(
                    null,
                    groupId,
                    item.type(),
                    item.id(),
                    normalizedOperatorId,
                    now)) != 1) {
                throw new IllegalStateException("视频资源项保存失败");
            }
        }
        addAll(affectedUserIds, roleGroupMapper.findUserIdsCoveringGroup(groupId));
        bumpUsers(affectedUserIds, "RESOURCE_GROUP_ITEMS_CHANGED");
    }

    @Override
    @Transactional
    public void replaceRoleGroups(
            long roleId,
            VideoScopeType type,
            Set<Long> groupIds) {
        replaceRoleGroups(roleId, type, groupIds, null);
    }

    @Override
    @Transactional
    public void replaceRoleGroups(
            long roleId,
            VideoScopeType type,
            Set<Long> groupIds,
            Long operatorId) {
        replaceRoleGroupsInternal(roleId, type, groupIds, operatorId, true);
    }

    @Override
    @Transactional
    public void replaceRoleGroupsForRoleUpdate(
            long roleId,
            VideoScopeType type,
            Set<Long> groupIds,
            Long operatorId) {
        replaceRoleGroupsInternal(roleId, type, groupIds, operatorId, false);
    }

    private void replaceRoleGroupsInternal(
            long roleId,
            VideoScopeType type,
            Set<Long> groupIds,
            Long operatorId,
            boolean invalidateUsers) {
        if (roleId <= 0) {
            throw new IllegalArgumentException("角色不存在");
        }
        Objects.requireNonNull(type, "video scope type must not be null");
        Objects.requireNonNull(groupIds, "groupIds must not be null");
        Long normalizedOperatorId = normalizeOperatorId(operatorId);
        RoleVideoScope role = roleGroupMapper.findRoleForUpdate(roleId);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在");
        }
        VideoScopeType.fromDatabaseValue(role.videoScopeType());
        if (role.isSystemRole() && type != VideoScopeType.ALL) {
            throw new AccessDeniedException("系统角色的视频范围必须保持 ALL");
        }

        Set<Long> normalizedGroupIds = normalizeGroupIds(groupIds);
        if (type == VideoScopeType.CUSTOM) {
            if (normalizedGroupIds.isEmpty()) {
                throw new IllegalArgumentException("CUSTOM 视频范围至少需要一个启用资源组");
            }
            if (groupMapper.countEnabledByIds(normalizedGroupIds) != normalizedGroupIds.size()) {
                throw new IllegalArgumentException("角色只能绑定存在且整条路径启用的资源组");
            }
        } else {
            normalizedGroupIds = Set.of();
        }

        if (roleGroupMapper.updateVideoScope(
                roleId, type.name(), normalizedOperatorId, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("角色视频范围保存失败");
        }
        roleGroupMapper.deleteByRoleId(roleId);
        for (Long groupId : normalizedGroupIds.stream().sorted().toList()) {
            if (roleGroupMapper.insert(roleId, groupId) != 1) {
                throw new IllegalStateException("角色视频资源组保存失败");
            }
        }
        if (invalidateUsers) {
            tokenVersionService.bumpUsersByRoleId(roleId, "ROLE_VIDEO_SCOPE_CHANGED");
        }
    }

    private Set<Long> moveInternal(long groupId, long parentId, Long operatorId) {
        if (groupId <= 0 || parentId <= 0 || groupId == parentId) {
            throw new IllegalArgumentException("非法的视频资源组移动");
        }
        ResourceGroup group = requireGroupForUpdate(groupId);
        if (group.isRoot()) {
            throw new AccessDeniedException("ROOT_VIDEO 不能移动");
        }
        ResourceGroup newParent = requireEnabledGroupForUpdate(parentId);

        List<ResourceGroupClosurePath> subtreePaths =
                groupMapper.findSubtreePathsForUpdate(groupId);
        List<ResourceGroupClosurePath> oldAncestorPaths =
                groupMapper.findAncestorPathsForUpdate(groupId);
        List<ResourceGroupClosurePath> newAncestorPaths =
                groupMapper.findAncestorPathsForUpdate(parentId);
        requireCompletePath(groupId, subtreePaths, false);
        requireCompletePath(groupId, oldAncestorPaths, true);
        requireCompletePath(parentId, newAncestorPaths, true);

        Set<Long> subtreeIds = new LinkedHashSet<>();
        for (ResourceGroupClosurePath path : subtreePaths) {
            subtreeIds.add(path.descendantId());
        }
        if (subtreeIds.contains(parentId)) {
            throw new IllegalArgumentException("不能将资源组移动到自身或其后代节点");
        }
        if (group.getParentId() == newParent.getId()) {
            return Set.of();
        }

        Set<Long> affectedUserIds = new LinkedHashSet<>();
        addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(groupId));
        addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(parentId));
        List<Long> externalAncestorIds = oldAncestorPaths.stream()
                .map(ResourceGroupClosurePath::ancestorId)
                .filter(ancestorId -> !subtreeIds.contains(ancestorId))
                .toList();
        for (long ancestorId : externalAncestorIds) {
            for (ResourceGroupClosurePath subtreePath : subtreePaths) {
                if (groupMapper.deletePath(ancestorId, subtreePath.descendantId()) != 1) {
                    throw new IllegalStateException("视频资源组闭包关系不完整");
                }
            }
        }
        for (ResourceGroupClosurePath ancestorPath : newAncestorPaths) {
            for (ResourceGroupClosurePath subtreePath : subtreePaths) {
                int depth = Math.addExact(
                        Math.addExact(ancestorPath.depth(), 1),
                        subtreePath.depth());
                if (groupMapper.insertPath(
                        ancestorPath.ancestorId(),
                        subtreePath.descendantId(),
                        depth) != 1) {
                    throw new IllegalStateException("视频资源组闭包关系重建失败");
                }
            }
        }
        if (groupMapper.updateParent(
                groupId, parentId, operatorId, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("视频资源组移动失败");
        }
        addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(groupId));
        addAll(affectedUserIds, roleGroupMapper.findUserIdsRelatedToGroup(parentId));
        return Set.copyOf(affectedUserIds);
    }

    private ResourceGroup requireGroupForUpdate(long groupId) {
        ResourceGroup group = groupMapper.findByIdForUpdate(groupId);
        if (group == null) {
            throw new IllegalArgumentException("视频资源组不存在");
        }
        return group;
    }

    private ResourceGroup requireEnabledGroupForUpdate(long groupId) {
        ResourceGroup group = requireGroupForUpdate(groupId);
        if (!group.isEnabled() || groupMapper.countEnabled(groupId) != 1) {
            throw new IllegalArgumentException("视频资源组不存在或路径已停用");
        }
        return group;
    }

    private void requireCompletePath(
            long expectedSelfId,
            List<ResourceGroupClosurePath> paths,
            boolean ancestorPath) {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalStateException("视频资源组闭包关系不完整");
        }
        boolean hasSelf = paths.stream().anyMatch(path -> path.depth() == 0
                && path.ancestorId() == expectedSelfId
                && path.descendantId() == expectedSelfId);
        boolean directionValid = paths.stream().allMatch(path -> ancestorPath
                ? path.descendantId() == expectedSelfId
                : path.ancestorId() == expectedSelfId);
        if (!hasSelf || !directionValid) {
            throw new IllegalStateException("视频资源组闭包关系不完整");
        }
    }

    private Set<Long> normalizeGroupIds(Collection<Long> groupIds) {
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        for (Long groupId : groupIds) {
            if (groupId == null || groupId <= 0) {
                throw new IllegalArgumentException("资源组ID非法");
            }
            result.add(groupId);
        }
        return Set.copyOf(result);
    }

    private void bumpUsers(Collection<Long> userIds, String reason) {
        LinkedHashSet<Long> normalized = new LinkedHashSet<>();
        addAll(normalized, userIds);
        if (!normalized.isEmpty()) {
            tokenVersionService.bumpUsers(Set.copyOf(normalized), reason);
        }
    }

    private void addAll(Set<Long> target, Collection<Long> values) {
        if (values == null) {
            return;
        }
        values.stream()
                .filter(Objects::nonNull)
                .filter(value -> value > 0)
                .sorted(Comparator.naturalOrder())
                .forEach(target::add);
    }

    private Long normalizeOperatorId(Long operatorId) {
        if (operatorId != null && operatorId <= 0) {
            throw new IllegalArgumentException("操作人ID非法");
        }
        return operatorId;
    }

    private String normalizeRequired(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private String normalizeOptional(String value, String fieldName, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }

    private void requireStatus(int status) {
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("视频资源组状态非法");
        }
    }

    private static final Comparator<TreeBuilder> TREE_ORDER = Comparator
            .comparingInt((TreeBuilder builder) -> builder.group.getOrderNum())
            .thenComparingLong(builder -> builder.group.getId());

    private static final class TreeBuilder {

        private final ResourceGroup group;
        private final List<TreeBuilder> children = new ArrayList<>();

        private TreeBuilder(ResourceGroup group) {
            this.group = group;
        }

        private ResourceGroupTreeNode toNode() {
            children.sort(TREE_ORDER);
            return new ResourceGroupTreeNode(
                    group.getId(),
                    group.getParentId(),
                    group.getGroupCode(),
                    group.getGroupName(),
                    group.getOrderNum(),
                    group.getStatus(),
                    group.getDescription(),
                    children.stream().map(TreeBuilder::toNode).toList());
        }
    }
}
