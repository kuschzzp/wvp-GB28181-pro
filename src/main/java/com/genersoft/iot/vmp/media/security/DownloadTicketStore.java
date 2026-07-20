package com.genersoft.iot.vmp.media.security;

import java.time.Duration;
import java.util.Optional;

public interface DownloadTicketStore {

    void save(String ticketHash, DownloadTicketClaims claims, Duration ttl);

    /** Atomically returns and removes a ticket. */
    Optional<DownloadTicketClaims> consume(String ticketHash);
}
