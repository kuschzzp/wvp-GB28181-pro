package com.genersoft.iot.vmp.media.security;

public interface MediaHookReplayGuard {

    boolean accept(
            MediaNodePrincipal principal,
            String requestPath,
            byte[] requestBody,
            String timestamp,
            String nonce);
}
