package com.genersoft.iot.vmp.auth.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ChangeUserStatusRequest(
        @Min(0) @Max(1) int status,
        boolean locked) {
}
