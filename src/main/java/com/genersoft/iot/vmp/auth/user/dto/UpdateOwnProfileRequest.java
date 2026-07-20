package com.genersoft.iot.vmp.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateOwnProfileRequest(
        @NotBlank @Size(max = 64) String nickname) {

    public UpdateOwnProfileRequest {
        nickname = nickname == null ? null : nickname.trim();
    }
}
