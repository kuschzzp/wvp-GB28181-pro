package com.genersoft.iot.vmp.media.security;

public record PlayTicketDecision(boolean allowed, String reason) {

    public static PlayTicketDecision allow() {
        return new PlayTicketDecision(true, "ALLOW");
    }

    public static PlayTicketDecision deny(String reason) {
        return new PlayTicketDecision(false, reason);
    }
}
