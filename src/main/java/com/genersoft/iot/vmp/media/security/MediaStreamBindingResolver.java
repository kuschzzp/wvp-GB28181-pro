package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

public interface MediaStreamBindingResolver {

    MediaStreamBinding resolve(VideoResourceType type, long resourceId);
}
