package com.genersoft.iot.vmp.auth.department.dto;

import java.util.List;

public record DepartmentTreeNode(
        long id,
        long parentId,
        String deptName,
        String deptCode,
        int orderNum,
        Long leaderUserId,
        int status,
        boolean readOnlyPath,
        List<DepartmentTreeNode> children) {

    public DepartmentTreeNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
