package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.dto.CreateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeNode;
import com.genersoft.iot.vmp.auth.department.dto.UpdateDepartmentCommand;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;

import java.util.List;
import java.util.Set;

public interface DepartmentTreeService {

    long create(CreateDepartmentCommand command, AuthPrincipal operator);

    long create(
            CreateDepartmentCommand command,
            AuthPrincipal operator,
            DepartmentScope scope);

    void move(long deptId, long newParentId, AuthPrincipal operator);

    void move(
            long deptId,
            long newParentId,
            AuthPrincipal operator,
            DepartmentScope scope);

    void update(
            UpdateDepartmentCommand command,
            AuthPrincipal operator,
            DepartmentScope scope);

    void delete(long deptId, AuthPrincipal operator);

    void delete(long deptId, AuthPrincipal operator, DepartmentScope scope);

    Set<Long> findEnabledDescendantIds(long deptId);

    List<DepartmentTreeNode> tree(DepartmentScope scope);
}
