package com.genersoft.iot.vmp.auth.apikey.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CreateApiKeyRequest(
        Long ownerId,
        @Size(max = 255) String app,
        @Size(max = 255) String remark,
        @JsonDeserialize(using = AbsoluteInstantDeserializer.class)
        Instant expiresAt,
        boolean enabled,
        @Size(max = 256) Set<Long> permissionMenuIds,
        @Size(max = 256) Set<Long> resourceGroupIds,
        @Size(max = 64) List<@Size(max = 128) String> ipAllowlist) {

    public CreateApiKeyCommand toCommand() {
        return new CreateApiKeyCommand(
                app,
                remark,
                expiresAt,
                enabled,
                permissionMenuIds,
                resourceGroupIds,
                ipAllowlist);
    }
}
