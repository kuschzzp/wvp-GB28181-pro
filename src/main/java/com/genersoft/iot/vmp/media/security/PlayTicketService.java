package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.media.security.dto.PlayTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;

public interface PlayTicketService {

    PlayTicketResponse issue(PlayTicketRequest request);

    PlayTicketResponse issueShare(SharePlayTicketGrant grant);

    PlayTicketDecision verify(MediaPlayHookRequest request);
}
