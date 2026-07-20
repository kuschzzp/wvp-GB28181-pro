package com.genersoft.iot.vmp.auth.service;

public interface LoginAttemptService {

    void assertAllowed(String username, String clientIp);

    void recordFailure(String username, String clientIp);

    void clear(String username, String clientIp);
}
