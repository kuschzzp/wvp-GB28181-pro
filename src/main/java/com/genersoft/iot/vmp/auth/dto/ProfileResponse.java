package com.genersoft.iot.vmp.auth.dto;

import java.util.Set;

public record ProfileResponse(
        UserSummary user,
        Set<String> roles,
        Set<String> permissions,
        long authzVersion,
        boolean systemUser) {
}
