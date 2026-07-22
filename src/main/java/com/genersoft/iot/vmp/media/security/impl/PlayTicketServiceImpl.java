package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.MediaAccessPolicy;
import com.genersoft.iot.vmp.media.security.MediaPlayHookRequest;
import com.genersoft.iot.vmp.media.security.MediaSchema;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;
import com.genersoft.iot.vmp.media.security.MediaStreamBindingResolver;
import com.genersoft.iot.vmp.media.security.PlayTicketClaims;
import com.genersoft.iot.vmp.media.security.PlayTicketDecision;
import com.genersoft.iot.vmp.media.security.PlayTicketService;
import com.genersoft.iot.vmp.media.security.PlayTicketStore;
import com.genersoft.iot.vmp.media.security.SharePlayTicketGrant;
import com.genersoft.iot.vmp.media.security.TicketedMediaUrlFactory;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
@Slf4j
public class PlayTicketServiceImpl implements PlayTicketService {

    static final Duration DEFAULT_TTL = Duration.ofSeconds(120);
    private static final int TICKET_BYTES = 32;
    private static final String HASH_DOMAIN = "play-ticket:";

    private final AuthorizationFacade authorizationFacade;
    private final CurrentPrincipalService currentPrincipalService;
    private final MediaStreamBindingResolver bindingResolver;
    private final PlayTicketStore ticketStore;
    private final ApiKeySecretHasher secretHasher;
    private final TokenVersionService tokenVersionService;
    private final TicketedMediaUrlFactory mediaUrlFactory;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Duration ttl;

    @Autowired
    public PlayTicketServiceImpl(
            AuthorizationFacade authorizationFacade,
            CurrentPrincipalService currentPrincipalService,
            MediaStreamBindingResolver bindingResolver,
            PlayTicketStore ticketStore,
            ApiKeySecretHasher secretHasher,
            TokenVersionService tokenVersionService,
            TicketedMediaUrlFactory mediaUrlFactory) {
        this(
                authorizationFacade,
                currentPrincipalService,
                bindingResolver,
                ticketStore,
                secretHasher,
                tokenVersionService,
                mediaUrlFactory,
                Clock.systemUTC(),
                new SecureRandom(),
                DEFAULT_TTL);
    }

    PlayTicketServiceImpl(
            AuthorizationFacade authorizationFacade,
            CurrentPrincipalService currentPrincipalService,
            MediaStreamBindingResolver bindingResolver,
            PlayTicketStore ticketStore,
            ApiKeySecretHasher secretHasher,
            TokenVersionService tokenVersionService,
            TicketedMediaUrlFactory mediaUrlFactory,
            Clock clock,
            SecureRandom secureRandom,
            Duration ttl) {
        this.authorizationFacade = Objects.requireNonNull(
                authorizationFacade, "authorizationFacade must not be null");
        this.currentPrincipalService = Objects.requireNonNull(
                currentPrincipalService, "currentPrincipalService must not be null");
        this.bindingResolver = Objects.requireNonNull(
                bindingResolver, "bindingResolver must not be null");
        this.ticketStore = Objects.requireNonNull(
                ticketStore, "ticketStore must not be null");
        this.secretHasher = Objects.requireNonNull(
                secretHasher, "secretHasher must not be null");
        this.tokenVersionService = Objects.requireNonNull(
                tokenVersionService, "tokenVersionService must not be null");
        this.mediaUrlFactory = Objects.requireNonNull(
                mediaUrlFactory, "mediaUrlFactory must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(
                secureRandom, "secureRandom must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("play ticket TTL must be positive");
        }
        this.ttl = ttl;
    }

    @Override
    public PlayTicketResponse issue(PlayTicketRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (request.resourceType() == null || request.resourceId() <= 0) {
            throw new IllegalArgumentException("valid resourceType and resourceId are required");
        }
        MediaSchema schema = MediaSchema.parse(request.schema());
        String permissionCode = MediaAccessPolicy.playPermission(request.resourceType());
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        requireTicketPrincipal(principal);

        VideoResourceType authorizationType =
                authorizationResourceType(request.resourceType());
        authorizationFacade.requireVideoResource(
                permissionCode, authorizationType, request.resourceId());
        MediaStreamBinding binding = bindingResolver.resolve(
                request.resourceType(), request.resourceId());
        requireExactResourceBinding(
                request.resourceType(), request.resourceId(), binding);

        return issueBoundTicket(
                principal.type(),
                principal.id(),
                principal.userId(),
                permissionCode,
                request.resourceType(),
                request.resourceId(),
                principal.tokenVersion(),
                binding,
                schema,
                null);
    }

    @Override
    public PlayTicketResponse issueShare(SharePlayTicketGrant grant) {
        Objects.requireNonNull(grant, "share grant must not be null");
        MediaSchema schema = MediaSchema.parse(grant.schema());
        MediaAccessPolicy.playPermission(grant.resourceType());
        if (!tokenVersionService.matches(
                grant.creatorUserId(), grant.authzVersion())) {
            throw new AccessDeniedException("分享创建者权限已经失效");
        }
        MediaStreamBinding binding = bindingResolver.resolve(
                grant.resourceType(), grant.resourceId());
        requireExactResourceBinding(
                grant.resourceType(), grant.resourceId(), binding);

        return issueBoundTicket(
                PrincipalType.SHARE,
                grant.creatorUserId(),
                grant.creatorUserId(),
                "video:stream:share",
                grant.resourceType(),
                grant.resourceId(),
                grant.authzVersion(),
                binding,
                schema,
                grant.shareExpiresAt());
    }

    private PlayTicketResponse issueBoundTicket(
            PrincipalType principalType,
            long principalId,
            long userId,
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId,
            long authzVersion,
            MediaStreamBinding binding,
            MediaSchema schema,
            Instant maximumExpiresAt) {

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        if (maximumExpiresAt != null && maximumExpiresAt.isBefore(expiresAt)) {
            expiresAt = maximumExpiresAt;
        }
        Duration effectiveTtl = Duration.between(issuedAt, expiresAt);
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new AccessDeniedException("播放授权已经过期");
        }
        String rawTicket = generateTicket();
        Set<MediaSchema> allowedSchemas = Set.of(schema);
        Map<MediaSchema, String> sources = mediaUrlFactory.create(
                        binding, rawTicket, allowedSchemas)
                .entrySet()
                .stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().toASCIIString()));
        PlayTicketClaims claims = new PlayTicketClaims(
                principalType,
                principalId,
                userId,
                permissionCode,
                resourceType,
                resourceId,
                binding.mediaServerId(),
                binding.app(),
                binding.stream(),
                allowedSchemas,
                authzVersion,
                issuedAt,
                expiresAt);
        try {
            ticketStore.save(hash(rawTicket), claims, effectiveTtl);
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException(
                    "播放票据存储暂时不可用", exception);
        }
        return new PlayTicketResponse(
                rawTicket, issuedAt, expiresAt, binding, allowedSchemas, sources);
    }

    private VideoResourceType authorizationResourceType(
            VideoResourceType ticketResourceType) {
        return ticketResourceType == VideoResourceType.AUDIO_TALK
                ? VideoResourceType.CHANNEL
                : ticketResourceType;
    }

    @Override
    public PlayTicketDecision verify(MediaPlayHookRequest request) {
        if (!validHookRequest(request)) {
            return PlayTicketDecision.deny("MALFORMED_REQUEST");
        }
        final MediaSchema requestedSchema;
        try {
            requestedSchema = MediaSchema.parse(request.schema());
        } catch (IllegalArgumentException exception) {
            return PlayTicketDecision.deny("UNKNOWN_SCHEMA");
        }

        final Optional<PlayTicketClaims> storedClaims;
        try {
            storedClaims = ticketStore.find(hash(request.ticket()));
        } catch (RuntimeException exception) {
            log.warn("播放票据校验存储不可用，已拒绝本次播放");
            return PlayTicketDecision.deny("STORE_UNAVAILABLE");
        }
        if (storedClaims.isEmpty()) {
            return PlayTicketDecision.deny("TICKET_NOT_FOUND");
        }

        PlayTicketClaims claims = storedClaims.get();
        Instant now = clock.instant();
        if (claims.issuedAt().isAfter(now) || !claims.expiresAt().isAfter(now)) {
            return PlayTicketDecision.deny("TICKET_EXPIRED");
        }
        if (!sameBinding(request, claims)) {
            return PlayTicketDecision.deny("BINDING_MISMATCH");
        }
        if (!claims.allowedSchemas().contains(requestedSchema)) {
            return PlayTicketDecision.deny("SCHEMA_NOT_ALLOWED");
        }
        if (claims.principalType() == PrincipalType.INTERNAL) {
            return PlayTicketDecision.deny("INVALID_PRINCIPAL");
        }
        if (!tokenVersionService.matches(claims.userId(), claims.authzVersion())) {
            return PlayTicketDecision.deny("AUTHORIZATION_CHANGED");
        }
        return PlayTicketDecision.allow();
    }

    private void requireTicketPrincipal(AuthPrincipal principal) {
        if (principal.type() == PrincipalType.INTERNAL
                || principal.userId() == null
                || principal.userId() <= 0
                || principal.id() <= 0) {
            throw new AccessDeniedException("当前主体不能签发外部播放票据");
        }
    }

    private void requireExactResourceBinding(
            VideoResourceType resourceType,
            long resourceId,
            MediaStreamBinding binding) {
        if (binding == null
                || binding.resourceType() != resourceType
                || binding.resourceId() != resourceId) {
            throw new IllegalStateException("活动媒体流与请求资源不一致");
        }
    }

    private boolean validHookRequest(MediaPlayHookRequest request) {
        return request != null
                && hasText(request.mediaServerId())
                && hasText(request.app())
                && hasText(request.stream())
                && hasText(request.schema())
                && hasText(request.ticket())
                && request.ticket().length() <= 256;
    }

    private boolean sameBinding(
            MediaPlayHookRequest request,
            PlayTicketClaims claims) {
        return claims.mediaServerId().equals(request.mediaServerId().trim())
                && claims.app().equals(request.app().trim())
                && claims.stream().equals(request.stream().trim());
    }

    private String generateTicket() {
        byte[] random = new byte[TICKET_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String hash(String rawTicket) {
        return secretHasher.hash(HASH_DOMAIN + rawTicket);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
