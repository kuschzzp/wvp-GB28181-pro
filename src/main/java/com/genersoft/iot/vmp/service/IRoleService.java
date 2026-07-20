package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.auth.dto.RoleCommand;
import com.genersoft.iot.vmp.storager.dao.dto.Role;

import java.util.List;
import java.util.Set;

public interface IRoleService {

    Role getRoleById(long id);

    default void saveRole(RoleCommand command, Set<Long> menuIds) {
        saveRole(command, menuIds, Set.of(), Set.of());
    }

    default void saveRole(RoleCommand command, Set<Long> menuIds, Set<Long> deptIds) {
        saveRole(command, menuIds, deptIds, Set.of());
    }

    void saveRole(
            RoleCommand command,
            Set<Long> menuIds,
            Set<Long> deptIds,
            Set<Long> resourceGroupIds);

    void changeStatus(long id, int status);

    Set<Long> getMenuIds(long roleId);

    Set<Long> getDepartmentIds(long roleId);

    Set<Long> getResourceGroupIds(long roleId);

    void delete(long id);

    List<Role> getAll();
}
