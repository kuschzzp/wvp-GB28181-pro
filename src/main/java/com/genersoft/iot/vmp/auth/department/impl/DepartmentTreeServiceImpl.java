package com.genersoft.iot.vmp.auth.department.impl;

import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.DepartmentMapper;
import com.genersoft.iot.vmp.auth.department.DepartmentTreeService;
import com.genersoft.iot.vmp.auth.department.dto.CreateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeNode;
import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeRow;
import com.genersoft.iot.vmp.auth.department.dto.UpdateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.model.Department;
import com.genersoft.iot.vmp.auth.department.model.DepartmentClosurePath;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.conf.exception.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class DepartmentTreeServiceImpl implements DepartmentTreeService {

    private final DepartmentMapper mapper;
    private final DepartmentCommandPolicy commandPolicy;

    public DepartmentTreeServiceImpl(
            DepartmentMapper mapper,
            DepartmentCommandPolicy commandPolicy) {
        this.mapper = mapper;
        this.commandPolicy = commandPolicy;
    }

    @Override
    @Transactional
    public long create(CreateDepartmentCommand command, AuthPrincipal operator) {
        requireSystemOperator(operator);
        return create(command, operator, DepartmentScope.all(operator.userId()));
    }

    @Override
    @Transactional
    public long create(
            CreateDepartmentCommand command,
            AuthPrincipal operator,
            DepartmentScope scope) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        long operatorId = requireOperatorUserId(operator);
        String name = normalizeRequired(command.deptName(), "部门名称", 64);
        String code = normalizeRequired(command.deptCode(), "部门编码", 64);
        requireStatus(command.status());
        if (command.parentId() <= 0) {
            throw new IllegalArgumentException("上级部门不存在");
        }
        commandPolicy.requireDepartmentInScope(command.parentId(), scope);

        Department parent = mapper.findByIdForUpdate(command.parentId());
        if (parent == null || !parent.isEnabled()) {
            throw new IllegalArgumentException("上级部门不存在或已停用");
        }
        List<DepartmentClosurePath> parentPaths = mapper.findAncestorPathsForUpdate(parent.getId());
        requireCompletePath(parent.getId(), parentPaths, true);
        if (mapper.countByCode(code) > 0) {
            throw new IllegalArgumentException("部门编码已存在");
        }

        LocalDateTime now = LocalDateTime.now();
        Department department = new Department();
        department.setParentId(parent.getId());
        department.setDeptName(name);
        department.setDeptCode(code);
        department.setOrderNum(command.orderNum());
        department.setLeaderUserId(command.leaderUserId());
        department.setStatus(command.status());
        department.setDeleted(0);
        department.setCreateBy(operatorId);
        department.setCreateTime(now);
        department.setUpdateBy(operatorId);
        department.setUpdateTime(now);

        if (mapper.insert(department) != 1 || department.getId() == null) {
            throw new IllegalStateException("部门创建失败");
        }
        int ancestorRows = mapper.insertAncestorPaths(parent.getId(), department.getId());
        if (ancestorRows != parentPaths.size() || mapper.insertSelfPath(department.getId()) != 1) {
            throw new IllegalStateException("部门闭包关系创建失败");
        }
        return department.getId();
    }

    @Override
    @Transactional
    public void move(long deptId, long newParentId, AuthPrincipal operator) {
        requireSystemOperator(operator);
        move(deptId, newParentId, operator, DepartmentScope.all(operator.userId()));
    }

    @Override
    @Transactional
    public void move(
            long deptId,
            long newParentId,
            AuthPrincipal operator,
            DepartmentScope scope) {
        if (deptId <= 0 || newParentId <= 0 || deptId == newParentId) {
            throw new IllegalArgumentException("非法的部门移动");
        }
        Objects.requireNonNull(scope, "scope must not be null");
        long operatorId = requireOperatorUserId(operator);
        commandPolicy.requireDepartmentInScope(deptId, scope);
        commandPolicy.requireDepartmentInScope(newParentId, scope);
        Department department = requireDepartmentForUpdate(deptId);
        Department newParent = requireDepartmentForUpdate(newParentId);
        if (department.isRoot()) {
            throw new IllegalArgumentException("ROOT 部门不能移动");
        }
        if (!newParent.isEnabled()) {
            throw new IllegalArgumentException("新上级部门已停用");
        }

        List<DepartmentClosurePath> subtreePaths = mapper.findSubtreePathsForUpdate(deptId);
        List<DepartmentClosurePath> oldAncestorPaths = mapper.findAncestorPathsForUpdate(deptId);
        List<DepartmentClosurePath> newAncestorPaths = mapper.findAncestorPathsForUpdate(newParentId);
        requireCompletePath(deptId, subtreePaths, false);
        requireCompletePath(deptId, oldAncestorPaths, true);
        requireCompletePath(newParentId, newAncestorPaths, true);

        Set<Long> subtreeIds = new LinkedHashSet<>();
        for (DepartmentClosurePath path : subtreePaths) {
            subtreeIds.add(path.descendantId());
        }
        if (subtreeIds.contains(newParentId)) {
            throw new IllegalArgumentException("不能将部门移动到自身或其后代节点");
        }
        if (department.getParentId() == newParentId) {
            return;
        }

        List<Long> externalAncestorIds = oldAncestorPaths.stream()
                .map(DepartmentClosurePath::ancestorId)
                .filter(ancestorId -> !subtreeIds.contains(ancestorId))
                .toList();
        for (long ancestorId : externalAncestorIds) {
            for (DepartmentClosurePath subtreePath : subtreePaths) {
                if (mapper.deletePath(ancestorId, subtreePath.descendantId()) != 1) {
                    throw new IllegalStateException("部门闭包关系不完整");
                }
            }
        }

        for (DepartmentClosurePath ancestorPath : newAncestorPaths) {
            for (DepartmentClosurePath subtreePath : subtreePaths) {
                int depth = Math.addExact(
                        Math.addExact(ancestorPath.depth(), 1),
                        subtreePath.depth());
                if (mapper.insertPath(
                        ancestorPath.ancestorId(), subtreePath.descendantId(), depth) != 1) {
                    throw new IllegalStateException("部门闭包关系重建失败");
                }
            }
        }
        if (mapper.updateParent(deptId, newParentId, operatorId, LocalDateTime.now()) != 1) {
            throw new IllegalStateException("部门移动失败");
        }
    }

    @Override
    @Transactional
    public void update(
            UpdateDepartmentCommand command,
            AuthPrincipal operator,
            DepartmentScope scope) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        long operatorId = requireOperatorUserId(operator);
        if (command.id() <= 0 || command.parentId() <= 0 || command.id() == command.parentId()) {
            throw new IllegalArgumentException("部门或上级部门非法");
        }
        String name = normalizeRequired(command.deptName(), "部门名称", 64);
        String code = normalizeRequired(command.deptCode(), "部门编码", 64);
        requireStatus(command.status());
        commandPolicy.requireDepartmentInScope(command.id(), scope);
        commandPolicy.requireDepartmentInScope(command.parentId(), scope);

        Department current = requireDepartmentForUpdate(command.id());
        if (current.isRoot()
                && (current.getParentId() != command.parentId()
                || command.status() != 0
                || !current.getDeptCode().equals(code))) {
            throw new AccessDeniedException("ROOT 部门的父级、编码和状态不能修改");
        }
        if (mapper.countByCodeExcludingId(code, command.id()) > 0) {
            throw new IllegalArgumentException("部门编码已存在");
        }
        if (current.getParentId() != command.parentId()) {
            move(command.id(), command.parentId(), operator, scope);
        }
        if (mapper.updateDetails(
                command.id(),
                name,
                code,
                command.orderNum(),
                command.leaderUserId(),
                command.status(),
                operatorId,
                LocalDateTime.now()) != 1) {
            throw new IllegalStateException("部门更新失败");
        }
    }

    @Override
    @Transactional
    public void delete(long deptId, AuthPrincipal operator) {
        requireSystemOperator(operator);
        delete(deptId, operator, DepartmentScope.all(operator.userId()));
    }

    @Override
    @Transactional
    public void delete(long deptId, AuthPrincipal operator, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        requireOperatorUserId(operator);
        commandPolicy.requireDepartmentInScope(deptId, scope);
        Department department = requireDepartmentForUpdate(deptId);
        if (department.isRoot()) {
            throw new IllegalArgumentException("ROOT 部门不能删除");
        }
        if (operator.deptId() != null && operator.deptId() == deptId) {
            throw new AccessDeniedException("不能删除本人的主部门");
        }
        if (mapper.countDirectChildren(deptId) > 0) {
            throw new BusinessException("部门存在子部门，不能删除", HttpStatus.CONFLICT);
        }
        if (mapper.countUsers(deptId) > 0) {
            throw new BusinessException("部门存在用户，不能删除", HttpStatus.CONFLICT);
        }
        if (mapper.deleteById(deptId) != 1) {
            throw new IllegalStateException("部门删除失败");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Long> findEnabledDescendantIds(long deptId) {
        if (deptId <= 0 || mapper.countEnabled(deptId) == 0) {
            return Set.of();
        }
        List<Long> ids = mapper.findEnabledDescendantIds(deptId);
        if (ids == null || !ids.contains(deptId)) {
            return Set.of();
        }
        return Set.copyOf(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentTreeNode> tree(DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        List<DepartmentTreeRow> rows = mapper.findVisibleTreeRows(scope);
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }

        Map<Long, TreeBuilder> nodes = new LinkedHashMap<>();
        for (DepartmentTreeRow row : rows) {
            nodes.put(row.getId(), new TreeBuilder(row));
        }
        List<TreeBuilder> roots = new ArrayList<>();
        for (TreeBuilder node : nodes.values()) {
            TreeBuilder parent = nodes.get(node.row.getParentId());
            if (parent == null) {
                roots.add(node);
            } else {
                parent.children.add(node);
            }
        }
        roots.sort(TREE_ORDER);
        return roots.stream().map(TreeBuilder::toNode).toList();
    }

    private Department requireDepartmentForUpdate(long deptId) {
        Department department = mapper.findByIdForUpdate(deptId);
        if (department == null || department.getDeleted() != 0) {
            throw new IllegalArgumentException("部门不存在");
        }
        return department;
    }

    private void requireCompletePath(
            long expectedSelfId,
            List<DepartmentClosurePath> paths,
            boolean ancestorPath) {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalStateException("部门闭包关系不完整");
        }
        boolean hasSelf = paths.stream().anyMatch(path -> path.depth() == 0
                && path.ancestorId() == expectedSelfId
                && path.descendantId() == expectedSelfId);
        boolean directionValid = paths.stream().allMatch(path -> ancestorPath
                ? path.descendantId() == expectedSelfId
                : path.ancestorId() == expectedSelfId);
        if (!hasSelf || !directionValid) {
            throw new IllegalStateException("部门闭包关系不完整");
        }
    }

    private long requireOperatorUserId(AuthPrincipal operator) {
        Objects.requireNonNull(operator, "operator must not be null");
        if (operator.userId() == null || operator.userId() <= 0) {
            throw new IllegalArgumentException("操作人必须绑定有效用户");
        }
        return operator.userId();
    }

    private void requireSystemOperator(AuthPrincipal operator) {
        requireOperatorUserId(operator);
        if (!operator.systemUser()) {
            throw new AccessDeniedException("无范围部门命令仅允许系统管理员调用");
        }
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

    private void requireStatus(int status) {
        if (status != 0 && status != 1) {
            throw new IllegalArgumentException("部门状态非法");
        }
    }

    private static final Comparator<TreeBuilder> TREE_ORDER = Comparator
            .comparingInt((TreeBuilder builder) -> builder.row.getOrderNum())
            .thenComparingLong(builder -> builder.row.getId());

    private static final class TreeBuilder {

        private final DepartmentTreeRow row;
        private final List<TreeBuilder> children = new ArrayList<>();

        private TreeBuilder(DepartmentTreeRow row) {
            this.row = row;
        }

        private DepartmentTreeNode toNode() {
            children.sort(TREE_ORDER);
            return new DepartmentTreeNode(
                    row.getId(),
                    row.getParentId(),
                    row.getDeptName(),
                    row.getDeptCode(),
                    row.getOrderNum(),
                    row.getLeaderUserId(),
                    row.getStatus(),
                    row.isReadOnlyPath(),
                    children.stream().map(TreeBuilder::toNode).toList());
        }
    }
}
