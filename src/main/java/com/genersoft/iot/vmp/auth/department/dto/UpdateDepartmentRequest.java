package com.genersoft.iot.vmp.auth.department.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record UpdateDepartmentRequest(
        @Positive long parentId,
        @NotBlank @Size(max = 64) String deptName,
        @NotBlank @Size(max = 64) String deptCode,
        int orderNum,
        Long leaderUserId,
        @Min(0) @Max(1) int status) {

    public UpdateDepartmentCommand toCommand(long id) {
        return new UpdateDepartmentCommand(
                id, parentId, deptName, deptCode, orderNum, leaderUserId, status);
    }
}
