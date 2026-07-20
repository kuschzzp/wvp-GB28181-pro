package com.genersoft.iot.vmp.auth.repository.impl;

import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.security.RequestIdResolver;
import com.genersoft.iot.vmp.storager.dao.AuthzAuditMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Repository
@Slf4j
public class MyBatisAuthorizationAuditSink implements AuthorizationAuditSink {

    private final AuthzAuditMapper mapper;

    public MyBatisAuthorizationAuditSink(AuthzAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void record(AuthzAuditRecord record) {
        try {
            mapper.insert(enrich(record));
        } catch (RuntimeException exception) {
            log.error("授权审计写入失败, principalType={}, principalId={}, permission={}",
                    record.principalType(), record.principalId(), record.permissionCode(), exception);
        }
    }

    private AuthzAuditRecord enrich(AuthzAuditRecord record) {
        HttpServletRequest request = currentRequest();
        String requestId = record.requestId();
        String clientIp = record.clientIp();
        if (request != null) {
            if (requestId == null || requestId.isBlank()) {
                requestId = request.getHeader(RequestIdResolver.HEADER_NAME);
            }
            if (clientIp == null || clientIp.isBlank()) {
                clientIp = request.getRemoteAddr();
            }
        }
        requestId = RequestIdResolver.normalize(requestId);
        return new AuthzAuditRecord(
                record.principalType(),
                record.principalId(),
                record.principalDeptId(),
                record.permissionCode(),
                record.resourceType(),
                record.resourceId(),
                record.allowed(),
                record.reasonCode(),
                requestId,
                clientIp,
                record.createTime());
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
