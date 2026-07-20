package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.media.security.dto.PlayableStreamRef;
import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Objects;

/**
 * Publishes a runtime binding and creates the URL-free response returned to browsers.
 */
@Service
public class PlayableStreamService {

    private static final Duration ACTIVE_BINDING_TTL = Duration.ofHours(6);

    private final ActiveMediaBindingRegistry bindingRegistry;
    private final LegacyMediaResponsePolicy legacyResponsePolicy;

    public PlayableStreamService(
            ActiveMediaBindingRegistry bindingRegistry,
            LegacyMediaResponsePolicy legacyResponsePolicy) {
        this.bindingRegistry = Objects.requireNonNull(
                bindingRegistry, "bindingRegistry must not be null");
        this.legacyResponsePolicy = Objects.requireNonNull(
                legacyResponsePolicy, "legacyResponsePolicy must not be null");
    }

    public StreamContent publish(StreamInfo streamInfo, VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        return publish(streamInfo, resource.type(), resource.id());
    }

    public StreamContent publish(
            StreamInfo streamInfo,
            VideoResourceType resourceType,
            long resourceId) {
        Objects.requireNonNull(streamInfo, "streamInfo must not be null");
        if (streamInfo.getMediaServer() == null) {
            throw new IllegalStateException("活动媒体流缺少媒体节点");
        }
        MediaStreamBinding binding = new MediaStreamBinding(
                streamInfo.getMediaServer().getId(),
                streamInfo.getApp(),
                streamInfo.getStream(),
                resourceType,
                resourceId);
        bindingRegistry.register(binding, ACTIVE_BINDING_TTL);
        StreamContent response = new StreamContent(
                streamInfo, new PlayableStreamRef(binding));
        response.setLegacyResponse(legacyResponsePolicy.create(
                streamInfo, resourceType, resourceId));
        return response;
    }
}
