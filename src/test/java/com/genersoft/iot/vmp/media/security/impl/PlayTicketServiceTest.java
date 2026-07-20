package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.MediaPlayHookRequest;
import com.genersoft.iot.vmp.media.security.MediaSchema;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;
import com.genersoft.iot.vmp.media.security.MediaStreamBindingResolver;
import com.genersoft.iot.vmp.media.security.PlayTicketClaims;
import com.genersoft.iot.vmp.media.security.PlayTicketDecision;
import com.genersoft.iot.vmp.media.security.PlayTicketStore;
import com.genersoft.iot.vmp.media.security.SharePlayTicketGrant;
import com.genersoft.iot.vmp.media.security.TicketedMediaUrlFactory;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.authentication.AuthenticationServiceException;

import java.security.SecureRandom;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayTicketServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T06:00:00Z");
    private static final MediaStreamBinding BINDING = new MediaStreamBinding(
            "zlm-1", "rtp", "34020000001320000001", VideoResourceType.CHANNEL, 42L);

    private AuthorizationFacade authorizationFacade;
    private CurrentPrincipalService currentPrincipalService;
    private MediaStreamBindingResolver bindingResolver;
    private TokenVersionService tokenVersionService;
    private ApiKeySecretHasher secretHasher;
    private TicketedMediaUrlFactory mediaUrlFactory;
    private InMemoryPlayTicketStore ticketStore;
    private MutableClock clock;
    private PlayTicketServiceImpl service;

    @BeforeEach
    void setUp() {
        authorizationFacade = mock(AuthorizationFacade.class);
        currentPrincipalService = mock(CurrentPrincipalService.class);
        bindingResolver = mock(MediaStreamBindingResolver.class);
        tokenVersionService = mock(TokenVersionService.class);
        secretHasher = mock(ApiKeySecretHasher.class);
        mediaUrlFactory = mock(TicketedMediaUrlFactory.class);
        ticketStore = new InMemoryPlayTicketStore();
        clock = new MutableClock(NOW);
        when(currentPrincipalService.requireCurrent()).thenReturn(userPrincipal(7L));
        when(bindingResolver.resolve(VideoResourceType.CHANNEL, 42L)).thenReturn(BINDING);
        when(secretHasher.hash(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenVersionService.matches(7L, 11L)).thenReturn(true);
        when(mediaUrlFactory.create(
                org.mockito.ArgumentMatchers.eq(BINDING),
                anyString(),
                org.mockito.ArgumentMatchers.anySet()))
                .thenAnswer(invocation -> {
                    Set<MediaSchema> schemas = invocation.getArgument(2);
                    MediaSchema schema = schemas.iterator().next();
                    return Map.of(schema, URI.create("https://media.example/live?ticket=test"));
                });
        service = new PlayTicketServiceImpl(
                authorizationFacade,
                currentPrincipalService,
                bindingResolver,
                ticketStore,
                secretHasher,
                tokenVersionService,
                mediaUrlFactory,
                clock,
                new SecureRandom(new byte[]{1, 2, 3, 4}),
                Duration.ofSeconds(120));
    }

    @Test
    void authorizesStableResourceBeforeResolvingAndStoresOpaqueBoundClaims() {
        PlayTicketResponse response = issue(MediaSchema.FLV);

        InOrder order = inOrder(authorizationFacade, bindingResolver);
        order.verify(authorizationFacade).requireVideoResource(
                "video:channel:play", VideoResourceType.CHANNEL, 42L);
        order.verify(bindingResolver).resolve(VideoResourceType.CHANNEL, 42L);
        byte[] decoded = Base64.getUrlDecoder().decode(response.ticket());
        assertEquals(32, decoded.length);
        assertEquals(NOW, response.issuedAt());
        assertEquals(NOW.plusSeconds(120), response.expiresAt());
        assertEquals(BINDING, response.binding());
        assertEquals(Set.of(MediaSchema.FLV), response.allowedSchemas());
        assertEquals(
                "https://media.example/live?ticket=test",
                response.sources().get(MediaSchema.FLV));

        PlayTicketClaims claims = ticketStore.onlyClaims();
        assertEquals(PrincipalType.USER, claims.principalType());
        assertEquals(7L, claims.principalId());
        assertEquals(7L, claims.userId());
        assertEquals("video:channel:play", claims.permissionCode());
        assertEquals(VideoResourceType.CHANNEL, claims.resourceType());
        assertEquals(42L, claims.resourceId());
        assertEquals(BINDING, claims.binding());
        assertEquals(11L, claims.authzVersion());
        assertEquals(Duration.ofSeconds(120), ticketStore.onlyTtl());
    }

    @Test
    void talkAudioUsesChannelAuthorizationButKeepsAnIndependentBinding() {
        MediaStreamBinding talkBinding = new MediaStreamBinding(
                "zlm-1", "talk", "audio-return",
                VideoResourceType.AUDIO_TALK, 42L);
        when(bindingResolver.resolve(VideoResourceType.AUDIO_TALK, 42L))
                .thenReturn(talkBinding);
        when(mediaUrlFactory.create(
                org.mockito.ArgumentMatchers.eq(talkBinding),
                anyString(),
                org.mockito.ArgumentMatchers.anySet()))
                .thenReturn(Map.of(
                        MediaSchema.WEBRTC,
                        URI.create("https://media.example/webrtc?type=play&ticket=test")));

        PlayTicketResponse response = service.issue(new PlayTicketRequest(
                VideoResourceType.AUDIO_TALK, 42L, MediaSchema.WEBRTC.name()));

        verify(authorizationFacade).requireVideoResource(
                "video:channel:talk", VideoResourceType.CHANNEL, 42L);
        verify(bindingResolver).resolve(VideoResourceType.AUDIO_TALK, 42L);
        assertEquals(talkBinding, response.binding());
        assertEquals(VideoResourceType.AUDIO_TALK,
                ticketStore.onlyClaims().resourceType());
        assertEquals("video:channel:talk",
                ticketStore.onlyClaims().permissionCode());
    }

    @Test
    void shareTicketHasDedicatedPrincipalAndCannotOutliveShare() {
        when(tokenVersionService.matches(7L, 11L)).thenReturn(true);

        PlayTicketResponse response = service.issueShare(new SharePlayTicketGrant(
                7L,
                11L,
                VideoResourceType.CHANNEL,
                42L,
                MediaSchema.HLS.name(),
                NOW.plusSeconds(30)));

        PlayTicketClaims claims = ticketStore.onlyClaims();
        assertEquals(PrincipalType.SHARE, claims.principalType());
        assertEquals("video:stream:share", claims.permissionCode());
        assertEquals(NOW.plusSeconds(30), response.expiresAt());
        assertEquals(Duration.ofSeconds(30), ticketStore.onlyTtl());
    }

    @Test
    void exactTicketAndBindingCanBeReusedWithinTtl() {
        PlayTicketResponse response = issue(MediaSchema.WS_FLV);
        MediaPlayHookRequest hook = hook(response.ticket(), "zlm-1", "rtp",
                "34020000001320000001", "ws-flv");

        assertTrue(service.verify(hook).allowed());
        clock.advance(Duration.ofSeconds(119));
        assertTrue(service.verify(hook).allowed());
        assertEquals(1, ticketStore.size(), "校验不能消费播放票据");
    }

    @Test
    void expiredTicketIsDenied() {
        PlayTicketResponse response = issue(MediaSchema.HLS);
        clock.advance(Duration.ofSeconds(120));

        PlayTicketDecision decision = service.verify(hook(
                response.ticket(), "zlm-1", "rtp", "34020000001320000001", "hls"));

        assertFalse(decision.allowed());
        assertEquals("TICKET_EXPIRED", decision.reason());
    }

    @Test
    void tamperedOrMissingRedisKeyIsDenied() {
        PlayTicketResponse response = issue(MediaSchema.FLV);

        PlayTicketDecision tampered = service.verify(hook(
                response.ticket() + "x", "zlm-1", "rtp", "34020000001320000001", "flv"));
        ticketStore.clear();
        PlayTicketDecision missing = service.verify(hook(
                response.ticket(), "zlm-1", "rtp", "34020000001320000001", "flv"));

        assertEquals("TICKET_NOT_FOUND", tampered.reason());
        assertEquals("TICKET_NOT_FOUND", missing.reason());
    }

    @Test
    void changingNodeAppStreamOrSchemaIsDenied() {
        PlayTicketResponse response = issue(MediaSchema.FLV);

        assertEquals("BINDING_MISMATCH", service.verify(hook(
                response.ticket(), "zlm-2", "rtp", "34020000001320000001", "flv")).reason());
        assertEquals("BINDING_MISMATCH", service.verify(hook(
                response.ticket(), "zlm-1", "live", "34020000001320000001", "flv")).reason());
        assertEquals("BINDING_MISMATCH", service.verify(hook(
                response.ticket(), "zlm-1", "rtp", "another-stream", "flv")).reason());
        assertEquals("SCHEMA_NOT_ALLOWED", service.verify(hook(
                response.ticket(), "zlm-1", "rtp", "34020000001320000001", "hls")).reason());
        assertEquals("UNKNOWN_SCHEMA", service.verify(hook(
                response.ticket(), "zlm-1", "rtp", "34020000001320000001", "made-up")).reason());
    }

    @Test
    void changedTokenVersionOrDisabledUserIsDenied() {
        PlayTicketResponse response = issue(MediaSchema.WEBRTC);
        when(tokenVersionService.matches(7L, 11L)).thenReturn(false);

        PlayTicketDecision decision = service.verify(hook(
                response.ticket(), "zlm-1", "rtp", "34020000001320000001", "webrtc"));

        assertFalse(decision.allowed());
        assertEquals("AUTHORIZATION_CHANGED", decision.reason());
        verify(tokenVersionService).matches(7L, 11L);
    }

    @Test
    void resolverCannotSubstituteAnotherResource() {
        when(bindingResolver.resolve(VideoResourceType.CHANNEL, 42L)).thenReturn(
                new MediaStreamBinding(
                        "zlm-1", "rtp", "same-stream", VideoResourceType.CHANNEL, 99L));

        assertThrows(IllegalStateException.class, () -> issue(MediaSchema.FLV));
        assertEquals(0, ticketStore.size());
    }

    @Test
    void redisFailureFailsClosedForIssueAndVerify() {
        PlayTicketStore failingStore = new PlayTicketStore() {
            @Override
            public void save(String ticketHash, PlayTicketClaims claims, Duration ttl) {
                throw new IllegalStateException("redis down");
            }

            @Override
            public Optional<PlayTicketClaims> find(String ticketHash) {
                throw new IllegalStateException("redis down");
            }
        };
        PlayTicketServiceImpl failingService = new PlayTicketServiceImpl(
                authorizationFacade,
                currentPrincipalService,
                bindingResolver,
                failingStore,
                secretHasher,
                tokenVersionService,
                mediaUrlFactory,
                clock,
                new SecureRandom(),
                Duration.ofSeconds(120));

        assertThrows(AuthenticationServiceException.class,
                () -> failingService.issue(new PlayTicketRequest(
                        VideoResourceType.CHANNEL, 42L, "flv")));
        assertEquals("STORE_UNAVAILABLE", failingService.verify(hook(
                "opaque-ticket", "zlm-1", "rtp", "stream", "flv")).reason());
    }

    private PlayTicketResponse issue(MediaSchema schema) {
        return service.issue(new PlayTicketRequest(
                VideoResourceType.CHANNEL, 42L, schema.name()));
    }

    private MediaPlayHookRequest hook(
            String ticket,
            String mediaServerId,
            String app,
            String stream,
            String schema) {
        return new MediaPlayHookRequest(mediaServerId, app, stream, schema, ticket);
    }

    private AuthPrincipal userPrincipal(long userId) {
        return new AuthPrincipal(
                PrincipalType.USER,
                userId,
                userId,
                "operator",
                10L,
                Set.of(2L),
                11L,
                false,
                Set.of("video:channel:play"));
    }

    private static final class InMemoryPlayTicketStore implements PlayTicketStore {
        private final Map<String, StoredTicket> values = new LinkedHashMap<>();

        @Override
        public void save(String ticketHash, PlayTicketClaims claims, Duration ttl) {
            values.put(ticketHash, new StoredTicket(claims, ttl));
        }

        @Override
        public Optional<PlayTicketClaims> find(String ticketHash) {
            return Optional.ofNullable(values.get(ticketHash)).map(StoredTicket::claims);
        }

        PlayTicketClaims onlyClaims() {
            return values.values().iterator().next().claims();
        }

        Duration onlyTtl() {
            return values.values().iterator().next().ttl();
        }

        int size() {
            return values.size();
        }

        void clear() {
            values.clear();
        }
    }

    private record StoredTicket(PlayTicketClaims claims, Duration ttl) {
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
