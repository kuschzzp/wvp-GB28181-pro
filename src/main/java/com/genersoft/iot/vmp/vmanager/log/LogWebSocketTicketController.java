package com.genersoft.iot.vmp.vmanager.log;

import com.genersoft.iot.vmp.conf.websocket.LogWebSocketTicketResponse;
import com.genersoft.iot.vmp.conf.websocket.LogWebSocketTicketService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LogWebSocketTicketController {

    private final LogWebSocketTicketService ticketService;

    public LogWebSocketTicketController(
            LogWebSocketTicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping("/api/log/ws-ticket")
    @PreAuthorize("@authz.require('operations:log:view')")
    public LogWebSocketTicketResponse issue() {
        return ticketService.issue();
    }
}
