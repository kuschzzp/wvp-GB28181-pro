package com.genersoft.iot.vmp.auth.model;

import java.time.LocalDateTime;

public record AuthzAuditRecord(
        PrincipalType principalType,
        long principalId,
        Long principalDeptId,
        String permissionCode,
        String resourceType,
        Long resourceId,
        boolean allowed,
        String reasonCode,
        String requestId,
        String clientIp,
        LocalDateTime createTime) {

    public String decision() {
        return allowed ? "ALLOW" : "DENY";
    }
}
