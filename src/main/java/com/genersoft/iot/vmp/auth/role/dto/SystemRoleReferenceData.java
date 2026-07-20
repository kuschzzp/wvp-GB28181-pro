package com.genersoft.iot.vmp.auth.role.dto;

import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeNode;
import com.genersoft.iot.vmp.storager.dao.dto.Menu;

import java.util.List;

public record SystemRoleReferenceData(
        List<Menu> menus,
        List<DepartmentTreeNode> departments) {

    public SystemRoleReferenceData {
        menus = menus == null ? List.of() : List.copyOf(menus);
        departments = departments == null ? List.of() : List.copyOf(departments);
    }
}
