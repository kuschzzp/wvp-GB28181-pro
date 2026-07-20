package com.genersoft.iot.vmp.auth.department.dto;

public record UpdateDepartmentCommand(
        long id,
        long parentId,
        String deptName,
        String deptCode,
        int orderNum,
        Long leaderUserId,
        int status) {
}
