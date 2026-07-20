package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.media.bean.ResultForOnPublish;
import com.genersoft.iot.vmp.media.bean.MediaServer;

/**
 * 媒体信息业务
 */
public interface IMediaService {

    /**
     * Audit-only compatibility for an ABL networkType that has not yet been
     * mapped. New external playback must use PlayTicketService.
     */
    @Deprecated(forRemoval = true)
    boolean authenticateLegacyPlayInAudit(
            String app,
            String stream,
            String callId);

    ResultForOnPublish authenticatePublish(MediaServer mediaServer, String app, String stream, String params);

    boolean closeStreamOnNoneReader(String mediaServerId, String app, String stream, String schema);
}
