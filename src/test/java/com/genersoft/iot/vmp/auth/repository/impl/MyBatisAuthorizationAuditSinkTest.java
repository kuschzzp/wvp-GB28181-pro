package com.genersoft.iot.vmp.auth.repository.impl;

import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.security.RequestIdResolver;
import com.genersoft.iot.vmp.storager.dao.AuthzAuditMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MyBatisAuthorizationAuditSinkTest {

    @Mock
    private AuthzAuditMapper mapper;

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void normalizesUntrustedRequestIdBeforeAuditInsert() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/device/query/devices");
        request.addHeader(RequestIdResolver.HEADER_NAME, "x".repeat(RequestIdResolver.MAX_LENGTH + 1));
        request.setRemoteAddr("2001:db8::1");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        MyBatisAuthorizationAuditSink sink = new MyBatisAuthorizationAuditSink(mapper);
        sink.record(new AuthzAuditRecord(
                PrincipalType.USER,
                12L,
                3L,
                "video:device:list",
                null,
                null,
                false,
                "MISSING_PERMISSION",
                null,
                null,
                LocalDateTime.of(2026, 7, 13, 12, 0)));

        ArgumentCaptor<AuthzAuditRecord> captor = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(mapper).insert(captor.capture());
        AuthzAuditRecord saved = captor.getValue();
        UUID.fromString(saved.requestId());
        assertEquals("2001:db8::1", saved.clientIp());
    }
}
