package com.genersoft.iot.vmp.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AdminUpdateUserRequest(
        @NotBlank @Size(max = 64) String nickname,
        @Positive long deptId) {

    public AdminUpdateUserRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
