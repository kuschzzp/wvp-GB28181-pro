package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.storager.dao.MediaServerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaHookCredentialServiceTest {

    private static final String PEPPER =
            "0123456789abcdef0123456789abcdef";
    private static final Instant NOW =
            Instant.parse("2026-07-15T08:00:00Z");

    @Mock private MediaServerMapper mediaServerMapper;

    private MediaHookSecretHasher secretHasher;
    private MediaHookCredentialService credentialService;

    @BeforeEach
    void setUp() {
        secretHasher = new MediaHookSecretHasher(PEPPER);
        credentialService = new MediaHookCredentialService(
                mediaServerMapper,
                secretHasher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void firstProvisionPersistsOnlyHashAndReturnsDerivedRawSecret() {
        MediaServer stored = node();
        MediaServer caller = node();
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(stored);
        when(mediaServerMapper.updateHookCredentials(any(MediaServer.class)))
                .thenReturn(1);

        String rawSecret = credentialService.ensureCredential(caller);

        assertEquals(1L, stored.getHookSecretVersion());
        assertTrue(secretHasher.matches(
                rawSecret, stored.getHookSecretHash()));
        assertEquals(stored.getHookSecretHash(), caller.getHookSecretHash());
        assertNull(stored.getPreviousHookSecretHash());
        assertNull(stored.getPreviousHookSecretExpiresAt());
    }

    @Test
    void rotationRetainsOldHashForExactlyFiveMinutes() {
        MediaServer stored = node();
        stored.setHookSecretVersion(4L);
        stored.setHookSecretHash(secretHasher.hash("old-secret"));
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(stored);
        when(mediaServerMapper.updateHookCredentials(any(MediaServer.class)))
                .thenReturn(1);

        MediaServer rotated = credentialService.rotate("zlm-1");

        assertEquals(5L, rotated.getHookSecretVersion());
        assertEquals(
                secretHasher.hash("old-secret"),
                rotated.getPreviousHookSecretHash());
        assertEquals(
                LocalDateTime.ofInstant(
                        NOW.plusSeconds(300), ZoneOffset.UTC),
                rotated.getPreviousHookSecretExpiresAt());
        String newSecret = secretHasher.deriveRawSecret("zlm-1", 5L);
        assertTrue(secretHasher.matches(
                newSecret, rotated.getHookSecretHash()));
    }

    @Test
    void retryDuringGracePeriodReusesCurrentRotation() {
        MediaServer stored = node();
        stored.setHookSecretVersion(5L);
        stored.setHookSecretHash(secretHasher.hash(
                secretHasher.deriveRawSecret("zlm-1", 5L)));
        stored.setPreviousHookSecretHash(secretHasher.hash("old-secret"));
        stored.setPreviousHookSecretExpiresAt(
                LocalDateTime.ofInstant(
                        NOW.plusSeconds(60), ZoneOffset.UTC));
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(stored);

        MediaServer retried = credentialService.rotate("zlm-1");

        assertEquals(5L, retried.getHookSecretVersion());
        assertEquals(
                secretHasher.hash("old-secret"),
                retried.getPreviousHookSecretHash());
    }

    @Test
    void configuredPepperMismatchDoesNotOverwriteExistingCredential() {
        MediaServer stored = node();
        stored.setHookSecretVersion(2L);
        stored.setHookSecretHash(secretHasher.hash("unrelated-secret"));
        when(mediaServerMapper.queryOne("zlm-1")).thenReturn(stored);

        assertThrows(
                IllegalStateException.class,
                () -> credentialService.ensureCredential(node()));
    }

    private MediaServer node() {
        MediaServer node = new MediaServer();
        node.setId("zlm-1");
        node.setType("zlm");
        node.setHookAuthEnabled(true);
        node.setHookAllowedCidrs("10.20.30.0/24");
        return node;
    }
}
