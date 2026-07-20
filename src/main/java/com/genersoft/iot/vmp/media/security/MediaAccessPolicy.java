package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.util.Objects;

public final class MediaAccessPolicy {

    private MediaAccessPolicy() {
    }

    public static String playPermission(VideoResourceType resourceType) {
        Objects.requireNonNull(resourceType, "resourceType must not be null");
        return switch (resourceType) {
            case GB_DEVICE, CHANNEL -> "video:channel:play";
            case JT_TERMINAL, JT_CHANNEL -> "video:jt:play";
            case STREAM_PROXY -> "video:proxy:play";
            case STREAM_PUSH -> "video:push:play";
            case CLOUD_RECORD -> "video:cloud-record:play";
            case DEVICE_RECORD -> "video:record:play";
            case AUDIO_TALK -> "video:channel:talk";
            case ALARM, MEDIA_STREAM, LEGACY_UNBOUND -> throw new IllegalArgumentException(
                    "resource type cannot issue a play ticket: " + resourceType);
        };
    }
}
