package com.genersoft.iot.vmp.auth.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record AssignRolesRequest(@NotNull Set<Long> roleIds) {
}
