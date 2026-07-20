package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.DownloadDeliveryType;
import com.genersoft.iot.vmp.media.security.DownloadFileGrant;
import com.genersoft.iot.vmp.media.security.DownloadPathGuard;
import com.genersoft.iot.vmp.media.security.DownloadPayload;
import com.genersoft.iot.vmp.media.security.DownloadResourceResolver;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.DownloadStorageType;
import com.genersoft.iot.vmp.media.security.DownloadTicketClaims;
import com.genersoft.iot.vmp.media.security.DownloadTicketException;
import com.genersoft.iot.vmp.media.security.DownloadTicketFailureReason;
import com.genersoft.iot.vmp.media.security.DownloadTicketGrant;
import com.genersoft.iot.vmp.media.security.DownloadTicketStore;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.access.AccessDeniedException;

import java.nio.file.Files;
import java.nio.file.Path;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DownloadTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T00:00:00Z");

    @TempDir
    Path tempDir;

    private DownloadResourceResolver resourceResolver;
    private AuthorizationFacade authorizationFacade;
    private CurrentPrincipalService currentPrincipalService;
    private TokenVersionService tokenVersionService;
    private InMemoryStore ticketStore;
    private DownloadPathGuard pathGuard;
    private ApiKeySecretHasher secretHasher;

    @BeforeEach
    void setUp() {
        resourceResolver = mock(DownloadResourceResolver.class);
        authorizationFacade = mock(AuthorizationFacade.class);
        currentPrincipalService = mock(CurrentPrincipalService.class);
        tokenVersionService = mock(TokenVersionService.class);
        ticketStore = new InMemoryStore();
        pathGuard = new DownloadPathGuard();
        secretHasher = mock(ApiKeySecretHasher.class);
        when(secretHasher.hash(anyString())).thenAnswer(
                invocation -> "hash:" + invocation.getArgument(0));
        when(currentPrincipalService.requireCurrent()).thenReturn(principal());
        when(tokenVersionService.matches(anyLong(), anyLong())).thenReturn(true);
    }

    @Test
    void issuesOpaqueOneTimeTicketBoundToServerResolvedRecord() throws Exception {
        DownloadTicketGrant grant = localFileGrant(41L);
        DownloadTicketRequest request = DownloadTicketRequest.single(
                DownloadResourceType.CLOUD_RECORD, 41L);
        when(resourceResolver.resolve(request)).thenReturn(grant);

        DownloadTicketServiceImpl service = serviceAt(NOW);
        DownloadTicketResponse response = service.issue(request);

        assertTrue(response.downloadUrl().startsWith("/api/media/download/"));
        assertFalse(response.downloadUrl().contains(tempDir.toString()));
        assertFalse(response.downloadUrl().contains("downloadFile"));
        verify(authorizationFacade).requireAllVideoResources(
                grant.permissionCode(), grant.authorizedResources());

        DownloadTicketClaims claims = service.consume(response.ticket());
        assertEquals(List.of(41L), claims.resourceIds());
        assertEquals(
                tempDir.resolve("record.mp4").toRealPath().toString(),
                claims.payload().files().getFirst().path());

        DownloadTicketException reused = assertThrows(
                DownloadTicketException.class,
                () -> service.consume(response.ticket()));
        assertEquals(DownloadTicketFailureReason.NOT_FOUND, reused.reason());
    }

    @Test
    void rejectsExpiredTicketAfterAtomicConsumption() throws Exception {
        DownloadTicketGrant grant = localFileGrant(42L);
        DownloadTicketServiceImpl issuer = serviceAt(NOW);
        DownloadTicketResponse response = issuer.issueTrusted(grant);

        DownloadTicketServiceImpl later = serviceAt(NOW.plusSeconds(121));
        DownloadTicketException expired = assertThrows(
                DownloadTicketException.class,
                () -> later.consume(response.ticket()));
        assertEquals(DownloadTicketFailureReason.EXPIRED, expired.reason());
        assertFalse(ticketStore.consume(hash(response.ticket())).isPresent());
    }

    @Test
    void rejectsTicketWhenAuthorizationVersionChanges() throws Exception {
        DownloadTicketResponse response = serviceAt(NOW)
                .issueTrusted(localFileGrant(43L));
        when(tokenVersionService.matches(7L, 11L)).thenReturn(false);

        DownloadTicketException changed = assertThrows(
                DownloadTicketException.class,
                () -> serviceAt(NOW.plusSeconds(1)).consume(response.ticket()));
        assertEquals(
                DownloadTicketFailureReason.AUTHORIZATION_CHANGED,
                changed.reason());
    }

    @Test
    void rejectsWholeZipWhenAnyResourceIsUnauthorized() throws Exception {
        DownloadTicketGrant grant = zipGrant(List.of(51L, 52L));
        DownloadTicketRequest request = DownloadTicketRequest.batch(
                DownloadResourceType.CLOUD_RECORD_ZIP, List.of(51L, 52L));
        when(resourceResolver.resolve(request)).thenReturn(grant);
        doThrow(new AccessDeniedException("denied"))
                .when(authorizationFacade)
                .requireAllVideoResources(
                        grant.permissionCode(), grant.authorizedResources());

        assertThrows(
                AccessDeniedException.class,
                () -> serviceAt(NOW).issue(request));
        assertTrue(ticketStore.values.isEmpty());
    }

    @Test
    void responseNeverExposesRemoteZlmDownloadUrl() {
        VideoResourceRef resource = new VideoResourceRef(
                VideoResourceType.CHANNEL, 61L);
        DownloadFileGrant remote = new DownloadFileGrant(
                DownloadStorageType.REMOTE_ZLM,
                "/opt/media/record/rtp/61.mp4",
                "/opt/media/record",
                "61.mp4",
                "zlm-1");
        DownloadTicketGrant grant = new DownloadTicketGrant(
                DownloadResourceType.CLOUD_RECORD,
                List.of(61L),
                "video:cloud-record:download",
                List.of(resource),
                new DownloadPayload(
                        DownloadDeliveryType.FILE,
                        List.of(remote),
                        null,
                        null,
                        null,
                        "video/mp4",
                        "61.mp4",
                        false));

        DownloadTicketResponse response = serviceAt(NOW).issueTrusted(grant);

        assertFalse(response.toString().contains("zlm-1"));
        assertFalse(response.toString().contains("/opt/media"));
        assertFalse(response.toString().contains("downloadFile"));
    }

    @Test
    void pathGuardRejectsTraversalAndOutsideAbsolutePath() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path outside = Files.writeString(tempDir.resolve("outside.mp4"), "outside");

        assertThrows(
                IllegalArgumentException.class,
                () -> pathGuard.resolveLocal(
                        root.toString(), "../outside.mp4", "outside.mp4"));
        assertThrows(
                IllegalArgumentException.class,
                () -> pathGuard.resolveLocal(
                        root.toString(), outside.toString(), "outside.mp4"));
    }

    @Test
    void pathGuardRejectsSymbolicLinkEscape() throws Exception {
        Path root = Files.createDirectories(tempDir.resolve("root"));
        Path outside = Files.writeString(tempDir.resolve("outside.mp4"), "outside");
        Path link = root.resolve("link.mp4");
        Files.createSymbolicLink(link, outside);

        assertThrows(
                IllegalArgumentException.class,
                () -> pathGuard.resolveLocal(
                        root.toString(), link.toString(), "link.mp4"));
    }

    private DownloadTicketServiceImpl serviceAt(Instant instant) {
        return new DownloadTicketServiceImpl(
                resourceResolver,
                authorizationFacade,
                currentPrincipalService,
                tokenVersionService,
                ticketStore,
                secretHasher,
                pathGuard,
                Clock.fixed(instant, ZoneOffset.UTC),
                new SecureRandom(new byte[] {1, 2, 3, 4}),
                Duration.ofMinutes(2));
    }

    private DownloadTicketGrant localFileGrant(long recordId) throws Exception {
        Path file = tempDir.resolve("record.mp4");
        if (!Files.exists(file)) {
            Files.writeString(file, "record");
        }
        DownloadFileGrant target = pathGuard.resolveLocal(
                tempDir.toString(), file.toString(), "record.mp4");
        VideoResourceRef resource = new VideoResourceRef(
                VideoResourceType.CHANNEL, recordId + 1000);
        return new DownloadTicketGrant(
                DownloadResourceType.CLOUD_RECORD,
                List.of(recordId),
                "video:cloud-record:download",
                List.of(resource),
                new DownloadPayload(
                        DownloadDeliveryType.FILE,
                        List.of(target),
                        null,
                        null,
                        null,
                        "video/mp4",
                        "record.mp4",
                        false));
    }

    private DownloadTicketGrant zipGrant(List<Long> recordIds) throws Exception {
        Path first = Files.writeString(tempDir.resolve("one.mp4"), "one");
        Path second = Files.writeString(tempDir.resolve("two.mp4"), "two");
        List<DownloadFileGrant> files = List.of(
                pathGuard.resolveLocal(tempDir.toString(), first.toString(), "one.mp4"),
                pathGuard.resolveLocal(tempDir.toString(), second.toString(), "two.mp4"));
        List<VideoResourceRef> resources = recordIds.stream()
                .map(id -> new VideoResourceRef(VideoResourceType.CHANNEL, id + 1000))
                .toList();
        return new DownloadTicketGrant(
                DownloadResourceType.CLOUD_RECORD_ZIP,
                recordIds,
                "video:cloud-record:download",
                resources,
                new DownloadPayload(
                        DownloadDeliveryType.ZIP,
                        files,
                        null,
                        null,
                        null,
                        "application/zip",
                        "records.zip",
                        false));
    }

    private AuthPrincipal principal() {
        return new AuthPrincipal(
                PrincipalType.USER,
                7L,
                7L,
                "operator",
                2L,
                Set.of(3L),
                11L,
                false,
                Set.of("video:cloud-record:download"));
    }

    private String hash(String rawTicket) {
        return "hash:download-ticket:" + rawTicket;
    }

    private static final class InMemoryStore implements DownloadTicketStore {

        private final Map<String, DownloadTicketClaims> values =
                new LinkedHashMap<>();

        @Override
        public void save(
                String ticketHash,
                DownloadTicketClaims claims,
                Duration ttl) {
            values.put(ticketHash, claims);
        }

        @Override
        public Optional<DownloadTicketClaims> consume(String ticketHash) {
            return Optional.ofNullable(values.remove(ticketHash));
        }
    }
}
