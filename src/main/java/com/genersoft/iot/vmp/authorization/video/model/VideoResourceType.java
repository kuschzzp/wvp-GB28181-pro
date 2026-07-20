package com.genersoft.iot.vmp.authorization.video.model;

public enum VideoResourceType {

    GB_DEVICE(true),
    CHANNEL(true),
    JT_TERMINAL(true),
    JT_CHANNEL(true),
    STREAM_PROXY(true),
    STREAM_PUSH(true),
    CLOUD_RECORD(false),
    DEVICE_RECORD(false),
    AUDIO_TALK(false),
    ALARM(false),
    MEDIA_STREAM(false),
    LEGACY_UNBOUND(false);

    private final boolean bindable;

    VideoResourceType(boolean bindable) {
        this.bindable = bindable;
    }

    public boolean isBindable() {
        return bindable;
    }
}
