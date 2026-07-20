package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record CloudRecordSourceBinding(
        VideoResourceType sourceType,
        long sourceId,
        long channelId) {

    private static final Set<VideoResourceType> SUPPORTED_SOURCE_TYPES =
            Set.copyOf(EnumSet.of(
                    VideoResourceType.CHANNEL,
                    VideoResourceType.STREAM_PUSH,
                    VideoResourceType.STREAM_PROXY));

    public CloudRecordSourceBinding {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        if (!SUPPORTED_SOURCE_TYPES.contains(sourceType)) {
            throw new IllegalArgumentException(
                    "云录像不支持来源类型: " + sourceType);
        }
        if (sourceId <= 0) {
            throw new IllegalArgumentException("sourceId must be positive");
        }
        if (channelId <= 0) {
            throw new IllegalArgumentException("channelId must be positive");
        }
    }

    public void applyTo(CloudRecordItem item) {
        Objects.requireNonNull(item, "cloud record item must not be null");
        item.setSourceType(sourceType.name());
        item.setSourceId(sourceId);
        item.setChannelId(channelId);
    }
}
