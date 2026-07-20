package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.time.Duration;
import java.util.Optional;

/**
 * Registry populated when an asynchronous play operation creates a runtime stream.
 */
public interface ActiveMediaBindingRegistry {

    void register(MediaStreamBinding binding, Duration ttl);

    Optional<MediaStreamBinding> find(VideoResourceType resourceType, long resourceId);

    void remove(VideoResourceType resourceType, long resourceId);
}
