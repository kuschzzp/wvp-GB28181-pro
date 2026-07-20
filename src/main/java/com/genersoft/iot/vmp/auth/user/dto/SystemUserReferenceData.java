package com.genersoft.iot.vmp.auth.user.dto;

import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeNode;
import com.genersoft.iot.vmp.storager.dao.dto.Role;

import java.util.List;

public record SystemUserReferenceData(
        List<DepartmentTreeNode> departments,
        List<Role> roles) {

    public SystemUserReferenceData {
        departments = departments == null ? List.of() : List.copyOf(departments);
        roles = roles == null ? List.of() : List.copyOf(roles);
    }
}
