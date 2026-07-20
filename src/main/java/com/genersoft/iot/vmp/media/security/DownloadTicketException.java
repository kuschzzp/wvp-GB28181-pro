package com.genersoft.iot.vmp.media.security;

public class DownloadTicketException extends RuntimeException {

    private final DownloadTicketFailureReason reason;

    public DownloadTicketException(DownloadTicketFailureReason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public DownloadTicketException(
            DownloadTicketFailureReason reason,
            String message,
            Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public DownloadTicketFailureReason reason() {
        return reason;
    }
}
