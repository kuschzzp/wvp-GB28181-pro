package com.genersoft.iot.vmp.auth.repository;

import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;

public interface AuthorizationAuditSink {

    void record(AuthzAuditRecord record);
}
