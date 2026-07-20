package com.genersoft.iot.vmp.conf.websocket;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.socket.server.standard.SpringConfigurator;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AuthenticatedWebSocketConfigurator extends SpringConfigurator {

    public static final String APPLICATION_PROTOCOL = "wvp-log-v1";
    public static final String TICKET_PROTOCOL_PREFIX = "wvp-log-ticket.";
    public static final String PRINCIPAL_PROPERTY =
            AuthenticatedWebSocketConfigurator.class.getName() + ".principal";

    private final LogWebSocketTicketService configuredTicketService;

    public AuthenticatedWebSocketConfigurator() {
        this.configuredTicketService = null;
    }

    AuthenticatedWebSocketConfigurator(
            LogWebSocketTicketService ticketService) {
        this.configuredTicketService = ticketService;
    }

    @Override
    public String getNegotiatedSubprotocol(
            List<String> supported,
            List<String> requested) {
        if (supported != null
                && requested != null
                && supported.contains(APPLICATION_PROTOCOL)
                && requested.contains(APPLICATION_PROTOCOL)) {
            return APPLICATION_PROTOCOL;
        }
        return "";
    }

    @Override
    public void modifyHandshake(
            ServerEndpointConfig endpointConfig,
            HandshakeRequest request,
            HandshakeResponse response) {
        try {
            URI requestUri = request.getRequestURI();
            if (requestUri == null || requestUri.getRawQuery() != null) {
                throw rejected();
            }
            List<String> protocols = requestedProtocols(request.getHeaders());
            if (protocols.size() != 2
                    || !protocols.contains(APPLICATION_PROTOCOL)) {
                throw rejected();
            }
            List<String> tickets = protocols.stream()
                    .filter(protocol -> protocol.startsWith(TICKET_PROTOCOL_PREFIX))
                    .toList();
            if (tickets.size() != 1) {
                throw rejected();
            }
            String rawTicket = tickets.getFirst().substring(
                    TICKET_PROTOCOL_PREFIX.length());
            AuthPrincipal principal = ticketService().consume(rawTicket);
            endpointConfig.getUserProperties().put(PRINCIPAL_PROPERTY, principal);
        } catch (SecurityException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw rejected(exception);
        }
    }

    private List<String> requestedProtocols(
            Map<String, List<String>> headers) {
        List<String> protocols = new ArrayList<>();
        if (headers == null) {
            return protocols;
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (!"Sec-WebSocket-Protocol".equalsIgnoreCase(entry.getKey())
                    || entry.getValue() == null) {
                continue;
            }
            for (String value : entry.getValue()) {
                if (value == null) {
                    continue;
                }
                for (String protocol : value.split(",")) {
                    if (!protocol.isBlank()) {
                        protocols.add(protocol.trim());
                    }
                }
            }
        }
        return List.copyOf(protocols);
    }

    private LogWebSocketTicketService ticketService() {
        if (configuredTicketService != null) {
            return configuredTicketService;
        }
        WebApplicationContext context =
                ContextLoader.getCurrentWebApplicationContext();
        if (context == null) {
            throw rejected();
        }
        return context.getBean(LogWebSocketTicketService.class);
    }

    private SecurityException rejected() {
        return new SecurityException("日志 WebSocket 握手认证失败");
    }

    private SecurityException rejected(Throwable cause) {
        return new SecurityException("日志 WebSocket 握手认证失败", cause);
    }
}
