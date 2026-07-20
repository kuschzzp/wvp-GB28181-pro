package com.genersoft.iot.vmp.auth.security;

public record SecurityError(int code, String msg, String requestId) {
}
