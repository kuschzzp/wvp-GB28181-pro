package com.genersoft.iot.vmp.auth.apikey;

public record GeneratedApiKey(
        String raw,
        String prefix,
        String last4,
        String hash) {
}
