package com.genersoft.iot.vmp.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record CreateUserRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 64) String nickname,
        Long deptId,
        @NotEmpty Set<Long> roleIds) {
}
