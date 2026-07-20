package com.genersoft.iot.vmp.conf.websocket;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.conf.webLog.LogChannel;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LogWebSocketAuthenticationTest {

    private static final Instant NOW = Instant.parse("2026-07-16T00:00:00Z");

    private CurrentPrincipalService currentPrincipalService;
    private TokenVersionService tokenVersionService;
    private ApiKeySecretHasher secretHasher;
    private InMemoryTicketStore ticketStore;

    @BeforeEach
    void setUp() {
        currentPrincipalService = mock(CurrentPrincipalService.class);
        tokenVersionService = mock(TokenVersionService.class);
        secretHasher = mock(ApiKeySecretHasher.class);
        ticketStore = new InMemoryTicketStore();
        when(secretHasher.hash(anyString())).thenAnswer(
                invocation -> "hash:" + invocation.getArgument(0));
        when(tokenVersionService.matches(anyLong(), anyLong())).thenReturn(true);
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(true));
        LogChannel.CHANNELS.clear();
    }

    @AfterEach
    void tearDown() {
        LogChannel.CHANNELS.clear();
    }

    @Test
    void issuesThirtySecondOneTimeTicketWithoutLongLivedToken() {
        LogWebSocketTicketServiceImpl service = serviceAt(NOW);

        LogWebSocketTicketResponse response = service.issue();

        assertEquals(NOW.plusSeconds(30), response.expiresAt());
        assertEquals(AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL,
                response.protocols().getFirst());
        assertTrue(response.protocols().get(1).startsWith(
                AuthenticatedWebSocketConfigurator.TICKET_PROTOCOL_PREFIX));
        assertFalse(response.toString().contains("access-token"));

        String ticket = response.protocols().get(1).substring(
                AuthenticatedWebSocketConfigurator.TICKET_PROTOCOL_PREFIX.length());
        AuthPrincipal consumed = service.consume(ticket);
        assertEquals("operator", consumed.username());
        assertThrows(BadCredentialsException.class, () -> service.consume(ticket));
    }

    @Test
    void rejectsExpiredTicketAndPrincipalWithoutLogPermission() {
        LogWebSocketTicketResponse response = serviceAt(NOW).issue();
        String ticket = ticket(response);

        assertThrows(
                CredentialsExpiredException.class,
                () -> serviceAt(NOW.plusSeconds(31)).consume(ticket));

        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        assertThrows(AccessDeniedException.class, () -> serviceAt(NOW).issue());
    }

    @Test
    void rejectsMissingAndQueryStringTicketsWithoutConsumingStore() {
        LogWebSocketTicketService ticketService = mock(LogWebSocketTicketService.class);
        AuthenticatedWebSocketConfigurator configurator =
                new AuthenticatedWebSocketConfigurator(ticketService);

        assertThrows(SecurityException.class, () -> configurator.modifyHandshake(
                endpointConfig(new LinkedHashMap<>()),
                handshake(URI.create("ws://localhost/channel/log"), List.of(
                        AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL)),
                response(new LinkedHashMap<>())));
        assertThrows(SecurityException.class, () -> configurator.modifyHandshake(
                endpointConfig(new LinkedHashMap<>()),
                handshake(
                        URI.create("ws://localhost/channel/log?access-token=long-lived"),
                        protocols("A".repeat(43))),
                response(new LinkedHashMap<>())));

        verifyNoInteractions(ticketService);
    }

    @Test
    void consumesSubprotocolTicketAndEchoesOnlyStableProtocol() {
        String ticket = "B".repeat(43);
        LogWebSocketTicketService ticketService = mock(LogWebSocketTicketService.class);
        when(ticketService.consume(ticket)).thenReturn(principal(true));
        AuthenticatedWebSocketConfigurator configurator =
                new AuthenticatedWebSocketConfigurator(ticketService);
        Map<String, Object> userProperties = new LinkedHashMap<>();
        Map<String, List<String>> responseHeaders = new LinkedHashMap<>();

        configurator.modifyHandshake(
                endpointConfig(userProperties),
                handshake(URI.create("ws://localhost/channel/log"), protocols(ticket)),
                response(responseHeaders));

        assertNotNull(userProperties.get(
                AuthenticatedWebSocketConfigurator.PRINCIPAL_PROPERTY));
        String negotiated = configurator.getNegotiatedSubprotocol(
                List.of(AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL),
                protocols(ticket));
        assertEquals(AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL, negotiated);
        assertFalse(responseHeaders.toString().contains(ticket));
        verify(ticketService).consume(ticket);
    }

    @Test
    void closesEstablishedConnectionBeforeSendingAfterTokenVersionChanges()
            throws Exception {
        Session session = mock(Session.class);
        EndpointConfig config = mock(EndpointConfig.class);
        RemoteEndpoint.Async remote = mock(RemoteEndpoint.Async.class);
        when(session.getId()).thenReturn("session-1");
        when(session.isOpen()).thenReturn(true);
        when(session.getAsyncRemote()).thenReturn(remote);
        when(config.getUserProperties()).thenReturn(Map.of(
                AuthenticatedWebSocketConfigurator.PRINCIPAL_PROPERTY,
                principal(true)));
        when(tokenVersionService.matches(7L, 11L)).thenReturn(true, false);
        LogChannel channel = new LogChannel(tokenVersionService);

        channel.onOpen(session, config);
        LogChannel.push("secret-log-line");

        verify(remote, never()).sendText(anyString());
        verify(session).close(any(CloseReason.class));
        assertFalse(LogChannel.CHANNELS.containsKey("session-1"));
    }

    private LogWebSocketTicketServiceImpl serviceAt(Instant instant) {
        return new LogWebSocketTicketServiceImpl(
                currentPrincipalService,
                tokenVersionService,
                ticketStore,
                secretHasher,
                Clock.fixed(instant, ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                Duration.ofSeconds(30));
    }

    private AuthPrincipal principal(boolean logPermission) {
        return new AuthPrincipal(
                PrincipalType.USER,
                7L,
                7L,
                "operator",
                2L,
                Set.of(3L),
                11L,
                false,
                logPermission ? Set.of("operations:log:view") : Set.of());
    }

    private String ticket(LogWebSocketTicketResponse response) {
        return response.protocols().get(1).substring(
                AuthenticatedWebSocketConfigurator.TICKET_PROTOCOL_PREFIX.length());
    }

    private List<String> protocols(String ticket) {
        return List.of(
                AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL,
                AuthenticatedWebSocketConfigurator.TICKET_PROTOCOL_PREFIX + ticket);
    }

    private ServerEndpointConfig endpointConfig(Map<String, Object> properties) {
        ServerEndpointConfig config = mock(ServerEndpointConfig.class);
        when(config.getUserProperties()).thenReturn(properties);
        return config;
    }

    private HandshakeRequest handshake(URI uri, List<String> protocols) {
        HandshakeRequest request = mock(HandshakeRequest.class);
        when(request.getRequestURI()).thenReturn(uri);
        when(request.getHeaders()).thenReturn(Map.of(
                "Sec-WebSocket-Protocol", List.of(String.join(", ", protocols))));
        return request;
    }

    private jakarta.websocket.HandshakeResponse response(
            Map<String, List<String>> headers) {
        jakarta.websocket.HandshakeResponse response =
                mock(jakarta.websocket.HandshakeResponse.class);
        when(response.getHeaders()).thenReturn(headers);
        return response;
    }

    private static final class InMemoryTicketStore
            implements LogWebSocketTicketStore {

        private final Map<String, LogWebSocketTicketClaims> values =
                new LinkedHashMap<>();

        @Override
        public void save(
                String ticketHash,
                LogWebSocketTicketClaims claims,
                Duration ttl) {
            values.put(ticketHash, claims);
        }

        @Override
        public Optional<LogWebSocketTicketClaims> consume(String ticketHash) {
            return Optional.ofNullable(values.remove(ticketHash));
        }
    }
}
