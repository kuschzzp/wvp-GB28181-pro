package com.genersoft.iot.vmp.auth.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangeOwnPasswordRequest(
        @NotBlank String oldPassword,
        @NotBlank @Size(min = 8, max = 128) String newPassword) {
}
