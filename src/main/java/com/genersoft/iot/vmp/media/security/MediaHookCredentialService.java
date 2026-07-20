package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.storager.dao.MediaServerMapper;
import com.genersoft.iot.vmp.utils.DateUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * Provisions derivable per-node Hook secrets while persisting only HMAC hashes.
 */
@Service
public class MediaHookCredentialService {

    private static final Duration PREVIOUS_SECRET_GRACE = Duration.ofMinutes(5);

    private final MediaServerMapper mediaServerMapper;
    private final MediaHookSecretHasher secretHasher;
    private final Clock clock;

    public MediaHookCredentialService(
            MediaServerMapper mediaServerMapper,
            MediaHookSecretHasher secretHasher) {
        this(mediaServerMapper, secretHasher, Clock.systemUTC());
    }

    MediaHookCredentialService(
            MediaServerMapper mediaServerMapper,
            MediaHookSecretHasher secretHasher,
            Clock clock) {
        this.mediaServerMapper = Objects.requireNonNull(
                mediaServerMapper, "mediaServerMapper must not be null");
        this.secretHasher = Objects.requireNonNull(
                secretHasher, "secretHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional
    public synchronized String ensureCredential(MediaServer mediaServer) {
        Objects.requireNonNull(mediaServer, "mediaServer must not be null");
        MediaServer stored = requireNode(mediaServer.getId());
        normalizeSettings(stored);
        if (stored.getHookSecretVersion() <= 0
                || !hasText(stored.getHookSecretHash())) {
            stored.setHookSecretVersion(1L);
            String rawSecret = secretHasher.deriveRawSecret(
                    stored.getId(), stored.getHookSecretVersion());
            stored.setHookSecretHash(secretHasher.hash(rawSecret));
            stored.setPreviousHookSecretHash(null);
            stored.setPreviousHookSecretExpiresAt(null);
            persist(stored);
            copyCredentialState(stored, mediaServer);
            return rawSecret;
        }

        String rawSecret = secretHasher.deriveRawSecret(
                stored.getId(), stored.getHookSecretVersion());
        if (!secretHasher.matches(rawSecret, stored.getHookSecretHash())) {
            if (secretHasher.usesEphemeralPepper()) {
                return reprovisionAfterEphemeralPepperChange(
                        stored, mediaServer);
            }
            throw new IllegalStateException(
                    "媒体 Hook pepper 与节点现有凭证不一致: " + stored.getId());
        }
        clearPreviousIfExpired(stored);
        copyCredentialState(stored, mediaServer);
        return rawSecret;
    }

    @Transactional
    public synchronized MediaServer rotate(String mediaServerId) {
        MediaServer stored = requireNode(mediaServerId);
        normalizeSettings(stored);
        if (hasText(stored.getPreviousHookSecretHash())
                && stored.getPreviousHookSecretExpiresAt() != null
                && stored.getPreviousHookSecretExpiresAt().isAfter(now())) {
            // A retry during the rollout grace period must re-apply the same
            // current credential instead of invalidating both old and new
            // node configurations with another rotation.
            return stored;
        }
        long currentVersion = Math.max(0L, stored.getHookSecretVersion());
        if (hasText(stored.getHookSecretHash()) && currentVersion > 0) {
            stored.setPreviousHookSecretHash(stored.getHookSecretHash());
            stored.setPreviousHookSecretExpiresAt(now().plus(PREVIOUS_SECRET_GRACE));
        } else {
            stored.setPreviousHookSecretHash(null);
            stored.setPreviousHookSecretExpiresAt(null);
        }
        stored.setHookSecretVersion(currentVersion + 1L);
        String rawSecret = secretHasher.deriveRawSecret(
                stored.getId(), stored.getHookSecretVersion());
        stored.setHookSecretHash(secretHasher.hash(rawSecret));
        persist(stored);
        return stored;
    }

    private String reprovisionAfterEphemeralPepperChange(
            MediaServer stored,
            MediaServer target) {
        stored.setHookSecretVersion(
                Math.max(0L, stored.getHookSecretVersion()) + 1L);
        String rawSecret = secretHasher.deriveRawSecret(
                stored.getId(), stored.getHookSecretVersion());
        stored.setHookSecretHash(secretHasher.hash(rawSecret));
        // The previous hash was produced with an unavailable process-local
        // pepper and can no longer be verified safely.
        stored.setPreviousHookSecretHash(null);
        stored.setPreviousHookSecretExpiresAt(null);
        persist(stored);
        copyCredentialState(stored, target);
        return rawSecret;
    }

    private MediaServer requireNode(String mediaServerId) {
        if (!hasText(mediaServerId)) {
            throw new IllegalArgumentException("媒体节点 ID 不能为空");
        }
        MediaServer stored = mediaServerMapper.queryOne(mediaServerId.trim());
        if (stored == null) {
            throw new IllegalArgumentException("媒体节点不存在: " + mediaServerId);
        }
        return stored;
    }

    private void normalizeSettings(MediaServer mediaServer) {
        if (mediaServer.getHookAuthEnabled() == null) {
            mediaServer.setHookAuthEnabled(true);
        }
    }

    private void clearPreviousIfExpired(MediaServer mediaServer) {
        if (mediaServer.getPreviousHookSecretExpiresAt() == null
                || mediaServer.getPreviousHookSecretExpiresAt().isAfter(now())) {
            return;
        }
        mediaServerMapper.clearExpiredPreviousHookSecret(
                mediaServer.getId(), now());
        mediaServer.setPreviousHookSecretHash(null);
        mediaServer.setPreviousHookSecretExpiresAt(null);
    }

    private void persist(MediaServer mediaServer) {
        mediaServer.setUpdateTime(DateUtil.getNow());
        if (mediaServerMapper.updateHookCredentials(mediaServer) != 1) {
            throw new IllegalStateException(
                    "媒体 Hook 凭证更新失败: " + mediaServer.getId());
        }
    }

    private void copyCredentialState(MediaServer source, MediaServer target) {
        target.setHookSecretHash(source.getHookSecretHash());
        target.setPreviousHookSecretHash(source.getPreviousHookSecretHash());
        target.setPreviousHookSecretExpiresAt(
                source.getPreviousHookSecretExpiresAt());
        target.setHookSecretVersion(source.getHookSecretVersion());
        target.setHookAllowedCidrs(source.getHookAllowedCidrs());
        target.setHookAuthEnabled(source.getHookAuthEnabled());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
