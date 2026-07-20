package com.genersoft.iot.vmp.auth.role.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChangeRoleStatusRequest(@Min(0) @Max(1) int status) {
}
