package com.genersoft.iot.vmp.authorization.video.model;

import java.util.Objects;

public record VideoResourceRef(VideoResourceType type, long id) {

    public VideoResourceRef {
        Objects.requireNonNull(type, "resource type must not be null");
        if (id <= 0) {
            throw new IllegalArgumentException("视频资源内部主键必须大于 0");
        }
    }
}
