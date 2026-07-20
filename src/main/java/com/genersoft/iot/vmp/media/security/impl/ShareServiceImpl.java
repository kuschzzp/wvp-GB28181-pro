package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.media.security.MediaAccessPolicy;
import com.genersoft.iot.vmp.media.security.PlayTicketService;
import com.genersoft.iot.vmp.media.security.SharePlayTicketGrant;
import com.genersoft.iot.vmp.media.security.ShareService;
import com.genersoft.iot.vmp.media.security.StreamShareAccessException;
import com.genersoft.iot.vmp.media.security.StreamShareStatus;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import com.genersoft.iot.vmp.media.security.dto.StreamShareCreateRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamSharePlayRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamShareResponse;
import com.genersoft.iot.vmp.storager.dao.StreamShareMapper;
import com.genersoft.iot.vmp.storager.dao.dto.StreamShare;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class ShareServiceImpl implements ShareService {

    static final String SHARE_PERMISSION = "video:stream:share";
    static final String REVOKE_PERMISSION = "video:stream:revoke-share";
    private static final int SHARE_ID_BYTES = 24;
    private static final int ID_INSERT_ATTEMPTS = 3;
    private static final Pattern SHARE_ID = Pattern.compile("^[A-Za-z0-9_-]{22,64}$");

    private final AuthorizationFacade authorizationFacade;
    private final CurrentPrincipalService currentPrincipalService;
    private final TokenVersionService tokenVersionService;
    private final StreamShareMapper shareMapper;
    private final PlayTicketService playTicketService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public ShareServiceImpl(
            AuthorizationFacade authorizationFacade,
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            StreamShareMapper shareMapper,
            PlayTicketService playTicketService,
            PasswordEncoder passwordEncoder) {
        this(
                authorizationFacade,
                currentPrincipalService,
                tokenVersionService,
                shareMapper,
                playTicketService,
                passwordEncoder,
                Clock.systemUTC(),
                new SecureRandom());
    }

    ShareServiceImpl(
            AuthorizationFacade authorizationFacade,
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            StreamShareMapper shareMapper,
            PlayTicketService playTicketService,
            PasswordEncoder passwordEncoder,
            Clock clock,
            SecureRandom secureRandom) {
        this.authorizationFacade = Objects.requireNonNull(
                authorizationFacade, "authorizationFacade must not be null");
        this.currentPrincipalService = Objects.requireNonNull(
                currentPrincipalService, "currentPrincipalService must not be null");
        this.tokenVersionService = Objects.requireNonNull(
                tokenVersionService, "tokenVersionService must not be null");
        this.shareMapper = Objects.requireNonNull(
                shareMapper, "shareMapper must not be null");
        this.playTicketService = Objects.requireNonNull(
                playTicketService, "playTicketService must not be null");
        this.passwordEncoder = Objects.requireNonNull(
                passwordEncoder, "passwordEncoder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(
                secureRandom, "secureRandom must not be null");
    }

    @Override
    @Transactional
    public StreamShareResponse create(StreamShareCreateRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.resourceType() == null || request.resourceId() <= 0) {
            throw new IllegalArgumentException("valid resourceType and resourceId are required");
        }
        Instant now = clock.instant();
        if (request.expiresAt() == null || !request.expiresAt().isAfter(now)) {
            throw new IllegalArgumentException("share expiry must be in the future");
        }
        if (request.maxViews() != null && request.maxViews() <= 0) {
            throw new IllegalArgumentException("maxViews must be positive");
        }

        String playPermission = MediaAccessPolicy.playPermission(request.resourceType());
        authorizationFacade.requirePermission(SHARE_PERMISSION);
        authorizationFacade.requireVideoResource(
                playPermission, request.resourceType(), request.resourceId());
        AuthPrincipal principal = requireShareCreator();

        StreamShare share = new StreamShare();
        share.setCreatorUserId(principal.userId());
        share.setResourceType(request.resourceType());
        share.setResourceId(request.resourceId());
        share.setExpiresAt(request.expiresAt());
        share.setMaxViews(request.maxViews());
        share.setViewCount(0);
        share.setPasscodeHash(hasText(request.passcode())
                ? passwordEncoder.encode(request.passcode())
                : null);
        share.setStatus(StreamShareStatus.ACTIVE);
        share.setAuthzVersion(principal.tokenVersion());
        share.setCreateTime(now);
        share.setUpdateTime(now);
        persistWithRandomId(share);
        return response(share);
    }

    @Override
    @Transactional
    public void revoke(String shareId) {
        String normalizedId = requireShareId(shareId);
        authorizationFacade.requirePermission(REVOKE_PERMISSION);
        AuthPrincipal principal = requireShareCreator();
        Instant now = clock.instant();
        int changed = principal.systemUser()
                ? shareMapper.revokeAny(normalizedId, now)
                : shareMapper.revokeByCreator(
                        normalizedId, principal.userId(), now);
        if (changed == 1) {
            return;
        }
        StreamShare existing = shareMapper.selectById(normalizedId);
        boolean ownsShare = existing != null
                && (principal.systemUser()
                || existing.getCreatorUserId() == principal.userId());
        if (ownsShare && existing.getStatus() == StreamShareStatus.REVOKED) {
            return;
        }
        throw denied(StreamShareAccessException.Reason.NOT_FOUND);
    }

    @Override
    @Transactional
    public PlayTicketResponse exchange(
            String shareId,
            StreamSharePlayRequest request) {
        String normalizedId = requireShareId(shareId);
        Objects.requireNonNull(request, "request must not be null");
        if (!hasText(request.schema())) {
            throw new IllegalArgumentException("schema must not be blank");
        }
        Instant now = clock.instant();
        StreamShare share = shareMapper.selectById(normalizedId);
        requireActive(share, now);
        requirePasscode(share, request.passcode());
        if (!tokenVersionService.matches(
                share.getCreatorUserId(), share.getAuthzVersion())) {
            throw denied(StreamShareAccessException.Reason.AUTHORIZATION_CHANGED);
        }
        if (shareMapper.consume(normalizedId, now) != 1) {
            requireActive(shareMapper.selectById(normalizedId), now);
            throw denied(StreamShareAccessException.Reason.EXHAUSTED);
        }

        return playTicketService.issueShare(new SharePlayTicketGrant(
                share.getCreatorUserId(),
                share.getAuthzVersion(),
                share.getResourceType(),
                share.getResourceId(),
                request.schema(),
                share.getExpiresAt()));
    }

    private void persistWithRandomId(StreamShare share) {
        DuplicateKeyException collision = null;
        for (int attempt = 0; attempt < ID_INSERT_ATTEMPTS; attempt++) {
            share.setShareId(generateShareId());
            try {
                if (shareMapper.insert(share) == 1) {
                    return;
                }
                throw new IllegalStateException("分享记录创建失败");
            } catch (DuplicateKeyException exception) {
                collision = exception;
            }
        }
        throw new IllegalStateException("分享标识生成冲突", collision);
    }

    private AuthPrincipal requireShareCreator() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        if (principal.type() == PrincipalType.INTERNAL
                || principal.userId() == null
                || principal.userId() <= 0
                || principal.tokenVersion() < 0) {
            throw new AccessDeniedException("当前主体不能管理公开分享");
        }
        return principal;
    }

    private void requireActive(StreamShare share, Instant now) {
        if (share == null) {
            throw denied(StreamShareAccessException.Reason.NOT_FOUND);
        }
        if (share.getStatus() != StreamShareStatus.ACTIVE) {
            throw denied(StreamShareAccessException.Reason.REVOKED);
        }
        if (share.getExpiresAt() == null || !share.getExpiresAt().isAfter(now)) {
            throw denied(StreamShareAccessException.Reason.EXPIRED);
        }
        if (share.getMaxViews() != null
                && share.getViewCount() >= share.getMaxViews()) {
            throw denied(StreamShareAccessException.Reason.EXHAUSTED);
        }
    }

    private void requirePasscode(StreamShare share, String passcode) {
        if (!hasText(share.getPasscodeHash())) {
            return;
        }
        if (!hasText(passcode)) {
            throw denied(StreamShareAccessException.Reason.PASSCODE_REQUIRED);
        }
        if (!passwordEncoder.matches(passcode, share.getPasscodeHash())) {
            throw denied(StreamShareAccessException.Reason.PASSCODE_INVALID);
        }
    }

    private StreamShareResponse response(StreamShare share) {
        return new StreamShareResponse(
                share.getShareId(),
                share.getResourceType(),
                share.getResourceId(),
                share.getExpiresAt(),
                share.getMaxViews(),
                share.getViewCount(),
                hasText(share.getPasscodeHash()),
                share.getStatus(),
                share.getCreateTime());
    }

    private String requireShareId(String shareId) {
        String normalized = shareId == null ? "" : shareId.trim();
        if (!SHARE_ID.matcher(normalized).matches()) {
            throw denied(StreamShareAccessException.Reason.NOT_FOUND);
        }
        return normalized;
    }

    private String generateShareId() {
        byte[] random = new byte[SHARE_ID_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private StreamShareAccessException denied(
            StreamShareAccessException.Reason reason) {
        return new StreamShareAccessException(reason);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
