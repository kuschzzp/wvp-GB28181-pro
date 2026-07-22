package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyIpAllowlist;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.storager.dao.MediaServerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Service
public class MediaHookAuthenticationService {

    private final MediaServerMapper mediaServerMapper;
    private final MediaHookSecretHasher secretHasher;
    private final Clock clock;

    @Autowired
    public MediaHookAuthenticationService(
            MediaServerMapper mediaServerMapper,
            MediaHookSecretHasher secretHasher) {
        this(mediaServerMapper, secretHasher, Clock.systemUTC());
    }

    MediaHookAuthenticationService(
            MediaServerMapper mediaServerMapper,
            MediaHookSecretHasher secretHasher,
            Clock clock) {
        this.mediaServerMapper = Objects.requireNonNull(
                mediaServerMapper, "mediaServerMapper must not be null");
        this.secretHasher = Objects.requireNonNull(
                secretHasher, "secretHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public MediaNodePrincipal authenticate(
            MediaNodeType requestedType,
            String mediaServerId,
            String rawSecret,
            InetAddress clientAddress) {
        if (requestedType == null
                || !hasText(mediaServerId)
                || !hasText(rawSecret)
                || rawSecret.length() > 256
                || clientAddress == null) {
            throw invalid();
        }

        final MediaServer mediaServer;
        try {
            mediaServer = mediaServerMapper.queryOne(mediaServerId.trim());
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException(
                    "媒体节点认证存储暂时不可用", exception);
        }
        if (mediaServer == null
                || Boolean.FALSE.equals(mediaServer.getHookAuthEnabled())
                || !typeMatches(requestedType, mediaServer.getType())
                || mediaServer.getHookSecretVersion() <= 0
                || !ApiKeyIpAllowlist.allows(
                        mediaServer.getHookAllowedCidrs(), clientAddress)) {
            throw invalid();
        }

        boolean currentMatches = secretHasher.matches(
                rawSecret, mediaServer.getHookSecretHash());
        boolean previousMatches = previousSecretValid(mediaServer)
                && secretHasher.matches(
                        rawSecret, mediaServer.getPreviousHookSecretHash());
        if (!currentMatches && !previousMatches) {
            throw invalid();
        }
        return new MediaNodePrincipal(
                mediaServer.getId(), requestedType,
                mediaServer.getHookSecretVersion());
    }

    private boolean typeMatches(
            MediaNodeType requestedType,
            String storedType) {
        try {
            return requestedType == MediaNodeType.parse(storedType);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean previousSecretValid(MediaServer mediaServer) {
        return hasText(mediaServer.getPreviousHookSecretHash())
                && mediaServer.getPreviousHookSecretExpiresAt() != null
                && mediaServer.getPreviousHookSecretExpiresAt().isAfter(now());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private BadCredentialsException invalid() {
        return new BadCredentialsException("媒体节点凭证无效");
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
