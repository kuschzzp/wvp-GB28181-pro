package com.genersoft.iot.vmp.conf.websocket;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;

public interface LogWebSocketTicketService {

    String LOG_VIEW_PERMISSION = "operations:log:view";

    LogWebSocketTicketResponse issue();

    AuthPrincipal consume(String rawTicket);
}
