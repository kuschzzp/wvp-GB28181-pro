package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;

public interface DownloadTicketService {

    DownloadTicketResponse issue(DownloadTicketRequest request);

    DownloadTicketResponse issueTrusted(DownloadTicketGrant grant);

    DownloadTicketClaims consume(String rawTicket);
}
