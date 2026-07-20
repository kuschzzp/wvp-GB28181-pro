package com.genersoft.iot.vmp.authorization.audit.dto;

import java.time.LocalDateTime;

public record AuthorizationAuditSummary(
        long id,
        String principalType,
        long principalId,
        Long principalDeptId,
        String permissionCode,
        String resourceType,
        Long resourceId,
        String decision,
        String reasonCode,
        String requestId,
        String clientIp,
        LocalDateTime createTime) {
}
