package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import com.genersoft.iot.vmp.media.security.dto.StreamShareCreateRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamSharePlayRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamShareResponse;

public interface ShareService {

    StreamShareResponse create(StreamShareCreateRequest request);

    void revoke(String shareId);

    PlayTicketResponse exchange(String shareId, StreamSharePlayRequest request);
}
