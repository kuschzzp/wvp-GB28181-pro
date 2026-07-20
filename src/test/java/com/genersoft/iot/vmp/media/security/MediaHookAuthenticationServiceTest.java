package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.storager.dao.MediaServerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaHookAuthenticationServiceTest {

    private static final String PEPPER =
            "0123456789abcdef0123456789abcdef";
    private static final Instant NOW =
            Instant.parse("2026-07-15T08:00:00Z");

    @Mock private MediaServerMapper mediaServerMapper;

    private MediaHookSecretHasher secretHasher;
    private MediaHookAuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        secretHasher = new MediaHookSecretHasher(PEPPER);
        authenticationService = new MediaHookAuthenticationService(
                mediaServerMapper,
                secretHasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void currentAndUnexpiredPreviousSecretsAreAccepted()
            throws Exception {
        MediaServer node = node();
        node.setHookSecretHash(secretHasher.hash("current-secret"));
        node.setPreviousHookSecretHash(secretHasher.hash("previous-secret"));
        node.setPreviousHookSecretExpiresAt(
                LocalDateTime.ofInstant(NOW.plusSeconds(60), ZoneOffset.UTC));
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(node);

        MediaNodePrincipal current = authenticationService.authenticate(
                MediaNodeType.ZLM,
                "zlm-1",
                "current-secret",
                InetAddress.getByName("10.20.30.40"));
        MediaNodePrincipal previous = authenticationService.authenticate(
                MediaNodeType.ZLM,
                "zlm-1",
                "previous-secret",
                InetAddress.getByName("10.20.30.40"));

        assertEquals("zlm:zlm-1", current.getName());
        assertEquals(current, previous);
    }

    @Test
    void expiredPreviousSecretAndWrongSourceAreRejected()
            throws Exception {
        MediaServer node = node();
        node.setHookSecretHash(secretHasher.hash("current-secret"));
        node.setPreviousHookSecretHash(secretHasher.hash("previous-secret"));
        node.setPreviousHookSecretExpiresAt(
                LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(node);

        assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(
                        MediaNodeType.ZLM,
                        "zlm-1",
                        "previous-secret",
                        InetAddress.getByName("10.20.30.40")));
        assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(
                        MediaNodeType.ZLM,
                        "zlm-1",
                        "current-secret",
                        InetAddress.getByName("10.21.30.40")));
    }

    @Test
    void disabledUnknownAndWrongNodeTypeAreRejected()
            throws Exception {
        MediaServer node = node();
        node.setHookSecretHash(secretHasher.hash("current-secret"));
        node.setHookAuthEnabled(false);
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(node);
        InetAddress address = InetAddress.getByName("10.20.30.40");

        assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(
                        MediaNodeType.ZLM,
                        "zlm-1",
                        "current-secret",
                        address));

        node.setHookAuthEnabled(true);
        assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(
                        MediaNodeType.ABL,
                        "zlm-1",
                        "current-secret",
                        address));

        when(mediaServerMapper.queryOne("missing")).thenReturn(null);
        assertThrows(
                BadCredentialsException.class,
                () -> authenticationService.authenticate(
                        MediaNodeType.ZLM,
                        "missing",
                        "current-secret",
                        address));
    }

    @Test
    void repositoryFailureFailsClosedAsAuthenticationFailure()
            throws Exception {
        when(mediaServerMapper.queryOne("zlm-1"))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThrows(
                AuthenticationServiceException.class,
                () -> authenticationService.authenticate(
                        MediaNodeType.ZLM,
                        "zlm-1",
                        "current-secret",
                        InetAddress.getByName("10.20.30.40")));
    }

    private MediaServer node() {
        MediaServer node = new MediaServer();
        node.setId("zlm-1");
        node.setType("zlm");
        node.setHookSecretVersion(7L);
        node.setHookAuthEnabled(true);
        node.setHookAllowedCidrs("10.20.30.0/24");
        return node;
    }
}
