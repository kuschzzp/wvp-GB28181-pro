package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;

public interface DownloadResourceResolver {

    DownloadTicketGrant resolve(DownloadTicketRequest request);

    DownloadTicketGrant resolveJtRecord(
            String downloadId,
            VideoResourceRef channelResource);

    DownloadTicketGrant resolveGbRecord(
            long recordId,
            VideoResourceRef channelResource);
}
