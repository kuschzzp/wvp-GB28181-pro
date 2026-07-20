package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.MediaSchema;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;
import com.genersoft.iot.vmp.media.security.PlayTicketService;
import com.genersoft.iot.vmp.media.security.SharePlayTicketGrant;
import com.genersoft.iot.vmp.media.security.StreamShareAccessException;
import com.genersoft.iot.vmp.media.security.StreamShareStatus;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import com.genersoft.iot.vmp.media.security.dto.StreamShareCreateRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamSharePlayRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamShareResponse;
import com.genersoft.iot.vmp.storager.dao.StreamShareMapper;
import com.genersoft.iot.vmp.storager.dao.dto.StreamShare;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StreamShareServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-15T09:00:00Z");
    private static final String SHARE_ID = "abcdefghijklmnopqrstuvwxyzABCDEF";

    private AuthorizationFacade authorizationFacade;
    private CurrentPrincipalService currentPrincipalService;
    private TokenVersionService tokenVersionService;
    private PlayTicketService playTicketService;
    private BCryptPasswordEncoder passwordEncoder;
    private InMemoryShareMapper shareMapper;
    private ShareServiceImpl service;

    @BeforeEach
    void setUp() {
        authorizationFacade = mock(AuthorizationFacade.class);
        currentPrincipalService = mock(CurrentPrincipalService.class);
        tokenVersionService = mock(TokenVersionService.class);
        playTicketService = mock(PlayTicketService.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        shareMapper = new InMemoryShareMapper();
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(false));
        when(tokenVersionService.matches(7L, 11L)).thenReturn(true);
        when(playTicketService.issueShare(any())).thenReturn(ticket());
        service = new ShareServiceImpl(
                authorizationFacade,
                currentPrincipalService,
                tokenVersionService,
                shareMapper,
                playTicketService,
                passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC),
                new SecureRandom(new byte[]{7, 1, 5}));
    }

    @Test
    void createRequiresShareAndResourcePermissionAndHashesPasscode() {
        StreamShareResponse response = service.create(new StreamShareCreateRequest(
                VideoResourceType.CHANNEL,
                42L,
                NOW.plusSeconds(3600),
                3L,
                "2468"));

        verify(authorizationFacade).requirePermission("video:stream:share");
        verify(authorizationFacade).requireVideoResource(
                "video:channel:play", VideoResourceType.CHANNEL, 42L);
        assertEquals(32, response.shareId().length());
        assertTrue(response.passcodeRequired());
        assertEquals(0, response.viewCount());
        StreamShare stored = shareMapper.only();
        assertNotEquals("2468", stored.getPasscodeHash());
        assertTrue(passwordEncoder.matches("2468", stored.getPasscodeHash()));
        assertEquals(11L, stored.getAuthzVersion());
    }

    @Test
    void deniedResourceCannotCreateShare() {
        org.mockito.Mockito.doThrow(new AccessDeniedException("denied"))
                .when(authorizationFacade).requireVideoResource(
                        "video:channel:play", VideoResourceType.CHANNEL, 42L);

        assertThrows(AccessDeniedException.class, () -> service.create(
                new StreamShareCreateRequest(
                        VideoResourceType.CHANNEL,
                        42L,
                        NOW.plusSeconds(60),
                        null,
                        null)));

        assertTrue(shareMapper.values.isEmpty());
    }

    @Test
    void expiredRevokedAndExhaustedSharesFailClosed() {
        StreamShare expired = share(SHARE_ID, NOW, null, 0, null);
        shareMapper.put(expired);
        assertReason(StreamShareAccessException.Reason.EXPIRED,
                () -> exchange(SHARE_ID, null));

        expired.setExpiresAt(NOW.plusSeconds(60));
        expired.setStatus(StreamShareStatus.REVOKED);
        assertReason(StreamShareAccessException.Reason.REVOKED,
                () -> exchange(SHARE_ID, null));

        expired.setStatus(StreamShareStatus.ACTIVE);
        expired.setMaxViews(1L);
        expired.setViewCount(1);
        assertReason(StreamShareAccessException.Reason.EXHAUSTED,
                () -> exchange(SHARE_ID, null));
        verify(playTicketService, never()).issueShare(any());
    }

    @Test
    void missingOrWrongPasscodeDoesNotConsumeAView() {
        StreamShare protectedShare = share(
                SHARE_ID,
                NOW.plusSeconds(60),
                3L,
                0,
                passwordEncoder.encode("2468"));
        shareMapper.put(protectedShare);

        assertReason(StreamShareAccessException.Reason.PASSCODE_REQUIRED,
                () -> exchange(SHARE_ID, null));
        assertReason(StreamShareAccessException.Reason.PASSCODE_INVALID,
                () -> exchange(SHARE_ID, "wrong"));

        assertEquals(0, protectedShare.getViewCount());
        assertEquals(ticket(), exchange(SHARE_ID, "2468"));
        assertEquals(1, protectedShare.getViewCount());
    }

    @Test
    void creatorAuthorizationChangeInvalidatesShareBeforeConsumption() {
        StreamShare share = share(
                SHARE_ID, NOW.plusSeconds(60), null, 0, null);
        shareMapper.put(share);
        when(tokenVersionService.matches(7L, 11L)).thenReturn(false);

        assertReason(StreamShareAccessException.Reason.AUTHORIZATION_CHANGED,
                () -> exchange(SHARE_ID, null));

        assertEquals(0, share.getViewCount());
        verify(playTicketService, never()).issueShare(any());
    }

    @Test
    void concurrentFinalViewCanOnlyIssueOneTicket() throws Exception {
        StreamShare share = share(
                SHARE_ID, NOW.plusSeconds(60), 1L, 0, null);
        shareMapper.put(share);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = pool.submit(() -> concurrentlyExchange(ready, start));
            Future<Object> second = pool.submit(() -> concurrentlyExchange(ready, start));
            ready.await();
            start.countDown();

            Object a = first.get();
            Object b = second.get();
            long successes = java.util.stream.Stream.of(a, b)
                    .filter(PlayTicketResponse.class::isInstance)
                    .count();
            long exhausted = java.util.stream.Stream.of(a, b)
                    .filter(StreamShareAccessException.class::isInstance)
                    .map(StreamShareAccessException.class::cast)
                    .filter(error -> error.reason()
                            == StreamShareAccessException.Reason.EXHAUSTED)
                    .count();
            assertEquals(1, successes);
            assertEquals(1, exhausted);
            assertEquals(1, share.getViewCount());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void creatorCanRevokeAndOtherUsersCannotDiscoverShare() {
        StreamShare share = share(
                SHARE_ID, NOW.plusSeconds(60), null, 0, null);
        shareMapper.put(share);

        service.revoke(SHARE_ID);
        assertEquals(StreamShareStatus.REVOKED, share.getStatus());

        share.setStatus(StreamShareStatus.ACTIVE);
        when(currentPrincipalService.requireCurrent()).thenReturn(new AuthPrincipal(
                PrincipalType.USER,
                8L,
                8L,
                "other",
                10L,
                Set.of(2L),
                4L,
                false,
                Set.of("video:stream:revoke-share")));
        assertReason(StreamShareAccessException.Reason.NOT_FOUND,
                () -> service.revoke(SHARE_ID));
        assertEquals(StreamShareStatus.ACTIVE, share.getStatus());
    }

    private Object concurrentlyExchange(
            CountDownLatch ready,
            CountDownLatch start) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            return exchange(SHARE_ID, null);
        } catch (StreamShareAccessException exception) {
            return exception;
        }
    }

    private PlayTicketResponse exchange(String shareId, String passcode) {
        return service.exchange(
                shareId, new StreamSharePlayRequest("HLS", passcode));
    }

    private void assertReason(
            StreamShareAccessException.Reason reason,
            org.junit.jupiter.api.function.Executable executable) {
        StreamShareAccessException exception = assertThrows(
                StreamShareAccessException.class, executable);
        assertEquals(reason, exception.reason());
    }

    private StreamShare share(
            String shareId,
            Instant expiresAt,
            Long maxViews,
            long viewCount,
            String passcodeHash) {
        StreamShare share = new StreamShare();
        share.setShareId(shareId);
        share.setCreatorUserId(7L);
        share.setResourceType(VideoResourceType.CHANNEL);
        share.setResourceId(42L);
        share.setExpiresAt(expiresAt);
        share.setMaxViews(maxViews);
        share.setViewCount(viewCount);
        share.setPasscodeHash(passcodeHash);
        share.setStatus(StreamShareStatus.ACTIVE);
        share.setAuthzVersion(11L);
        share.setCreateTime(NOW.minusSeconds(60));
        share.setUpdateTime(NOW.minusSeconds(60));
        return share;
    }

    private AuthPrincipal principal(boolean systemUser) {
        return new AuthPrincipal(
                PrincipalType.USER,
                7L,
                7L,
                "creator",
                10L,
                Set.of(2L),
                11L,
                systemUser,
                Set.of("video:stream:share", "video:stream:revoke-share"));
    }

    private PlayTicketResponse ticket() {
        return new PlayTicketResponse(
                "ticket",
                NOW,
                NOW.plusSeconds(60),
                new MediaStreamBinding(
                        "zlm-1", "rtp", "stream-1",
                        VideoResourceType.CHANNEL, 42L),
                Set.of(MediaSchema.HLS),
                Map.of(MediaSchema.HLS, "https://media.example/hls?ticket=ticket"));
    }

    private static final class InMemoryShareMapper implements StreamShareMapper {
        private final Map<String, StreamShare> values = new java.util.concurrent.ConcurrentHashMap<>();

        @Override
        public synchronized int insert(StreamShare share) {
            if (values.containsKey(share.getShareId())) {
                throw new DuplicateKeyException("duplicate share id");
            }
            values.put(share.getShareId(), share);
            return 1;
        }

        @Override
        public StreamShare selectById(String shareId) {
            return values.get(shareId);
        }

        @Override
        public synchronized int consume(String shareId, Instant now) {
            StreamShare share = values.get(shareId);
            if (share == null
                    || share.getStatus() != StreamShareStatus.ACTIVE
                    || !share.getExpiresAt().isAfter(now)
                    || (share.getMaxViews() != null
                    && share.getViewCount() >= share.getMaxViews())) {
                return 0;
            }
            share.setViewCount(share.getViewCount() + 1);
            share.setUpdateTime(now);
            return 1;
        }

        @Override
        public synchronized int revokeByCreator(
                String shareId, long creatorUserId, Instant now) {
            StreamShare share = values.get(shareId);
            if (share == null
                    || share.getCreatorUserId() != creatorUserId
                    || share.getStatus() != StreamShareStatus.ACTIVE) {
                return 0;
            }
            share.setStatus(StreamShareStatus.REVOKED);
            share.setUpdateTime(now);
            return 1;
        }

        @Override
        public synchronized int revokeAny(String shareId, Instant now) {
            StreamShare share = values.get(shareId);
            if (share == null || share.getStatus() != StreamShareStatus.ACTIVE) {
                return 0;
            }
            share.setStatus(StreamShareStatus.REVOKED);
            share.setUpdateTime(now);
            return 1;
        }

        void put(StreamShare share) {
            values.put(share.getShareId(), share);
        }

        StreamShare only() {
            assertFalse(values.isEmpty());
            return values.values().iterator().next();
        }
    }
}
