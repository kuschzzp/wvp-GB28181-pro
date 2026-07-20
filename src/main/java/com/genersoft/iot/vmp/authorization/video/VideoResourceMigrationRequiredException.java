package com.genersoft.iot.vmp.authorization.video;

public class VideoResourceMigrationRequiredException extends IllegalStateException {

    private final String reasonCode;

    public VideoResourceMigrationRequiredException(
            String reasonCode,
            String message) {
        super(message);
        this.reasonCode = reasonCode;
    }

    public String getReasonCode() {
        return reasonCode;
    }
}
