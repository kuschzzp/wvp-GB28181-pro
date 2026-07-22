package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.media.security.DownloadAccessProperties;
import com.genersoft.iot.vmp.media.security.DownloadDeliveryType;
import com.genersoft.iot.vmp.media.security.DownloadFileGrant;
import com.genersoft.iot.vmp.media.security.DownloadPathGuard;
import com.genersoft.iot.vmp.media.security.DownloadPayload;
import com.genersoft.iot.vmp.media.security.DownloadResourceResolver;
import com.genersoft.iot.vmp.media.security.DownloadTicketClaims;
import com.genersoft.iot.vmp.media.security.DownloadTicketException;
import com.genersoft.iot.vmp.media.security.DownloadTicketFailureReason;
import com.genersoft.iot.vmp.media.security.DownloadTicketGrant;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.DownloadTicketStore;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class DownloadTicketServiceImpl implements DownloadTicketService {

    private static final int TICKET_BYTES = 32;
    private static final String HASH_DOMAIN = "download-ticket:";

    private final DownloadResourceResolver resourceResolver;
    private final AuthorizationFacade authorizationFacade;
    private final CurrentPrincipalService currentPrincipalService;
    private final TokenVersionService tokenVersionService;
    private final DownloadTicketStore ticketStore;
    private final ApiKeySecretHasher secretHasher;
    private final DownloadPathGuard pathGuard;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Duration ttl;

    @Autowired
    public DownloadTicketServiceImpl(
            DownloadResourceResolver resourceResolver,
            AuthorizationFacade authorizationFacade,
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            DownloadTicketStore ticketStore,
            ApiKeySecretHasher secretHasher,
            DownloadPathGuard pathGuard,
            DownloadAccessProperties properties) {
        this(
                resourceResolver,
                authorizationFacade,
                currentPrincipalService,
                tokenVersionService,
                ticketStore,
                secretHasher,
                pathGuard,
                Clock.systemUTC(),
                new SecureRandom(),
                properties.ticketTtl());
    }

    DownloadTicketServiceImpl(
            DownloadResourceResolver resourceResolver,
            AuthorizationFacade authorizationFacade,
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            DownloadTicketStore ticketStore,
            ApiKeySecretHasher secretHasher,
            DownloadPathGuard pathGuard,
            Clock clock,
            SecureRandom secureRandom,
            Duration ttl) {
        this.resourceResolver = Objects.requireNonNull(
                resourceResolver, "resourceResolver must not be null");
        this.authorizationFacade = Objects.requireNonNull(
                authorizationFacade, "authorizationFacade must not be null");
        this.currentPrincipalService = Objects.requireNonNull(
                currentPrincipalService, "currentPrincipalService must not be null");
        this.tokenVersionService = Objects.requireNonNull(
                tokenVersionService, "tokenVersionService must not be null");
        this.ticketStore = Objects.requireNonNull(
                ticketStore, "ticketStore must not be null");
        this.secretHasher = Objects.requireNonNull(
                secretHasher, "secretHasher must not be null");
        this.pathGuard = Objects.requireNonNull(pathGuard, "pathGuard must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(
                secureRandom, "secureRandom must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("download ticket TTL must be positive");
        }
        this.ttl = ttl;
    }

    @Override
    public DownloadTicketResponse issue(DownloadTicketRequest request) {
        return issueTrusted(resourceResolver.resolve(request));
    }

    @Override
    public DownloadTicketResponse issueTrusted(DownloadTicketGrant grant) {
        Objects.requireNonNull(grant, "download grant must not be null");
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        requireTicketPrincipal(principal);
        authorizationFacade.requireAllVideoResources(
                grant.permissionCode(), grant.authorizedResources());

        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        String rawTicket = generateTicket();
        DownloadTicketClaims claims = new DownloadTicketClaims(
                principal.type(),
                principal.id(),
                principal.userId(),
                principal.tokenVersion(),
                grant.resourceType(),
                grant.resourceIds(),
                grant.permissionCode(),
                grant.authorizedResources(),
                grant.payload(),
                issuedAt,
                expiresAt);
        try {
            ticketStore.save(hash(rawTicket), claims, ttl);
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException(
                    "下载票据存储暂时不可用", exception);
        }
        return new DownloadTicketResponse(
                rawTicket,
                issuedAt,
                expiresAt,
                "/api/media/download/" + rawTicket,
                grant.payload().fileName(),
                grant.payload().contentType());
    }

    @Override
    public DownloadTicketClaims consume(String rawTicket) {
        if (rawTicket == null
                || rawTicket.isBlank()
                || rawTicket.length() > 256
                || !rawTicket.matches("[A-Za-z0-9_-]{40,128}")) {
            throw failure(DownloadTicketFailureReason.NOT_FOUND);
        }
        final Optional<DownloadTicketClaims> stored;
        try {
            stored = ticketStore.consume(hash(rawTicket));
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException(
                    "下载票据存储暂时不可用", exception);
        }
        DownloadTicketClaims claims = stored.orElseThrow(
                () -> failure(DownloadTicketFailureReason.NOT_FOUND));
        Instant now = clock.instant();
        if (claims.issuedAt().isAfter(now) || !claims.expiresAt().isAfter(now)) {
            throw failure(DownloadTicketFailureReason.EXPIRED);
        }
        if (!tokenVersionService.matches(
                claims.userId(), claims.authzVersion())) {
            throw failure(DownloadTicketFailureReason.AUTHORIZATION_CHANGED);
        }
        return withRevalidatedPayload(claims);
    }

    private DownloadTicketClaims withRevalidatedPayload(
            DownloadTicketClaims claims) {
        DownloadPayload payload = claims.payload();
        if (payload.deliveryType() != DownloadDeliveryType.FILE
                && payload.deliveryType() != DownloadDeliveryType.ZIP) {
            return claims;
        }
        List<DownloadFileGrant> files = new ArrayList<>(payload.files().size());
        for (DownloadFileGrant file : payload.files()) {
            files.add(pathGuard.revalidate(file));
        }
        DownloadPayload revalidated = new DownloadPayload(
                payload.deliveryType(),
                files,
                payload.opaqueId(),
                payload.phoneNumber(),
                payload.channelNumber(),
                payload.contentType(),
                payload.fileName(),
                payload.inline());
        return new DownloadTicketClaims(
                claims.principalType(),
                claims.principalId(),
                claims.userId(),
                claims.authzVersion(),
                claims.resourceType(),
                claims.resourceIds(),
                claims.permissionCode(),
                claims.authorizedResources(),
                revalidated,
                claims.issuedAt(),
                claims.expiresAt());
    }

    private void requireTicketPrincipal(AuthPrincipal principal) {
        if ((principal.type() != PrincipalType.USER
                && principal.type() != PrincipalType.API_KEY)
                || principal.userId() == null
                || principal.userId() <= 0
                || principal.id() <= 0) {
            throw new AccessDeniedException("当前主体不能签发下载票据");
        }
    }

    private String generateTicket() {
        byte[] random = new byte[TICKET_BYTES];
        secureRandom.nextBytes(random);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String hash(String rawTicket) {
        return secretHasher.hash(HASH_DOMAIN + rawTicket);
    }

    private DownloadTicketException failure(DownloadTicketFailureReason reason) {
        return new DownloadTicketException(reason, reason.name());
    }
}
