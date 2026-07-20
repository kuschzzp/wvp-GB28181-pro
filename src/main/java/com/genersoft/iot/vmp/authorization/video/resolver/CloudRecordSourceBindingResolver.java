package com.genersoft.iot.vmp.authorization.video.resolver;

import java.util.Optional;

public interface CloudRecordSourceBindingResolver {

    CloudRecordSourceBinding resolveForNewRecord(
            String callId,
            String app,
            String stream);

    Optional<CloudRecordSourceBinding> findByCallIdSession(String callId);

    Optional<CloudRecordSourceBinding> findByAppStreamSession(
            String app,
            String stream);

    Optional<CloudRecordSourceBinding> findByUnifiedMapping(
            String app,
            String stream);
}
