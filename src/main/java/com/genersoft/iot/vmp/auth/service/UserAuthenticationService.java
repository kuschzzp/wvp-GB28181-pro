package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.dto.LoginRequest;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;

public interface UserAuthenticationService {

    LoginResponse login(LoginRequest request, String clientIp);

    LoginResponse legacyMd5Login(String username, String clientMd5, String clientIp);
}
