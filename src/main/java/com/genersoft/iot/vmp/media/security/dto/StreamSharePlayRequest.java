package com.genersoft.iot.vmp.media.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StreamSharePlayRequest(
        @NotBlank @Size(max = 32) String schema,
        @Size(max = 128) String passcode) {
}
