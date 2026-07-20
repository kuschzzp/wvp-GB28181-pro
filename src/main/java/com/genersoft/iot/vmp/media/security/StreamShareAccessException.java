package com.genersoft.iot.vmp.media.security;

public class StreamShareAccessException extends RuntimeException {

    public enum Reason {
        NOT_FOUND,
        EXPIRED,
        REVOKED,
        EXHAUSTED,
        PASSCODE_REQUIRED,
        PASSCODE_INVALID,
        AUTHORIZATION_CHANGED
    }

    private final Reason reason;

    public StreamShareAccessException(Reason reason) {
        super(reason == null ? "share access denied" : reason.name());
        this.reason = reason == null ? Reason.NOT_FOUND : reason;
    }

    public Reason reason() {
        return reason;
    }
}
