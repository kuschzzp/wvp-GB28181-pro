package com.genersoft.iot.vmp.media.security;

import java.time.Duration;
import java.util.Optional;

public interface PlayTicketStore {

    void save(String ticketHash, PlayTicketClaims claims, Duration ttl);

    Optional<PlayTicketClaims> find(String ticketHash);
}
