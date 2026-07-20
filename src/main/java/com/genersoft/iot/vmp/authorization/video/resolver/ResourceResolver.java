package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.util.Set;

public interface ResourceResolver {

    Set<VideoResourceType> supportedTypes();

    VideoResourceRef requireExisting(VideoResourceRef resource);

    VideoResourceRef resolveToAuthorizable(VideoResourceRef resource);

    default Set<VideoResourceRef> resolveAuthorizationCandidates(VideoResourceRef resource) {
        return Set.of(resolveToAuthorizable(resource));
    }
}
