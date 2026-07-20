package com.genersoft.iot.vmp.authorization.audit.dto;

import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

public record AuthorizationAuditQuery(
        int page,
        int count,
        String permissionCode,
        String decision,
        String principalType,
        Long principalId,
        String resourceType,
        Long resourceId,
        LocalDateTime startTime,
        LocalDateTime endTime) {

    private static final int MAX_PAGE_SIZE = 200;
    private static final Set<String> DECISIONS = Set.of("ALLOW", "DENY");
    private static final Set<String> PRINCIPAL_TYPES = Set.of("USER", "API_KEY", "INTERNAL");

    public AuthorizationAuditQuery {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (count < 1 || count > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        permissionCode = normalize(permissionCode, 128, "权限编码");
        decision = normalizeUppercase(decision, "授权结果");
        principalType = normalizeUppercase(principalType, "主体类型");
        resourceType = normalizeUppercase(resourceType, "资源类型");
        if (decision != null && !DECISIONS.contains(decision)) {
            throw new IllegalArgumentException("授权结果只能是 ALLOW 或 DENY");
        }
        if (principalType != null && !PRINCIPAL_TYPES.contains(principalType)) {
            throw new IllegalArgumentException("主体类型非法");
        }
        if (principalId != null && principalId <= 0) {
            throw new IllegalArgumentException("主体 ID 非法");
        }
        if (resourceId != null && resourceId <= 0) {
            throw new IllegalArgumentException("资源 ID 非法");
        }
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
    }

    private static String normalizeUppercase(String value, String fieldName) {
        String normalized = normalize(value, 32, fieldName);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }

    private static String normalize(String value, int maxLength, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "长度不能超过 " + maxLength);
        }
        return normalized;
    }
}
