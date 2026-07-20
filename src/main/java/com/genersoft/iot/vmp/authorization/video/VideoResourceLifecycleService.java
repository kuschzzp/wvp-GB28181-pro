package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;

import java.util.Collection;

public interface VideoResourceLifecycleService {

    void remove(VideoResourceRef resource);

    void removeAll(Collection<VideoResourceRef> resources);

    void migrate(
            VideoResourceRef source,
            VideoResourceRef target,
            Long operatorId);

    void migrateUnique(
            VideoResourceRef source,
            Collection<VideoResourceRef> candidates,
            String stableSource,
            Long operatorId);
}
