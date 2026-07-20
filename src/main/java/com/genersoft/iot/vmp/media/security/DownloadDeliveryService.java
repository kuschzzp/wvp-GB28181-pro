package com.genersoft.iot.vmp.media.security;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public interface DownloadDeliveryService {

    void deliver(DownloadTicketClaims claims, HttpServletResponse response)
            throws IOException;
}
