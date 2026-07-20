package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;

public interface TokenRevocationService {

    void revoke(VerifiedAccessToken token);

    boolean isRevoked(String jti);
}
