package com.genersoft.iot.vmp.authorization.resourcegroup.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ResourceGroupRequest(
        @Positive long parentId,
        @NotBlank @Size(max = 64) String groupCode,
        @NotBlank @Size(max = 64) String groupName,
        int orderNum,
        @Min(0) @Max(1) int status,
        @Size(max = 500) String description) {
}
