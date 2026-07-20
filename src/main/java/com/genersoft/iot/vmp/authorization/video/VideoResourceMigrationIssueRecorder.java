package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;

import java.util.Collection;

public interface VideoResourceMigrationIssueRecorder {

    void record(
            VideoResourceRef source,
            Collection<VideoResourceRef> candidates,
            String stableSource,
            Long operatorId,
            String reasonCode);
}
