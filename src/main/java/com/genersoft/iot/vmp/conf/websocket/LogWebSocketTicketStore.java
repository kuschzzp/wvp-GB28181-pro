package com.genersoft.iot.vmp.conf.websocket;

import java.time.Duration;
import java.util.Optional;

public interface LogWebSocketTicketStore {

    void save(
            String ticketHash,
            LogWebSocketTicketClaims claims,
            Duration ttl);

    Optional<LogWebSocketTicketClaims> consume(String ticketHash);
}
