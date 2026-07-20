package com.genersoft.iot.vmp.conf.websocket;

import com.genersoft.iot.vmp.auth.apikey.ApiKeySecretHasher;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class LogWebSocketTicketServiceImpl
        implements LogWebSocketTicketService {

    private static final int TICKET_BYTES = 32;
    private static final String HASH_DOMAIN = "log-websocket-ticket:";
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(30);

    private final CurrentPrincipalService currentPrincipalService;
    private final TokenVersionService tokenVersionService;
    private final LogWebSocketTicketStore ticketStore;
    private final ApiKeySecretHasher secretHasher;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Duration ttl;

    public LogWebSocketTicketServiceImpl(
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            LogWebSocketTicketStore ticketStore,
            ApiKeySecretHasher secretHasher) {
        this(
                currentPrincipalService,
                tokenVersionService,
                ticketStore,
                secretHasher,
                Clock.systemUTC(),
                new SecureRandom(),
                DEFAULT_TTL);
    }

    LogWebSocketTicketServiceImpl(
            CurrentPrincipalService currentPrincipalService,
            TokenVersionService tokenVersionService,
            LogWebSocketTicketStore ticketStore,
            ApiKeySecretHasher secretHasher,
            Clock clock,
            SecureRandom secureRandom,
            Duration ttl) {
        this.currentPrincipalService = Objects.requireNonNull(
                currentPrincipalService, "currentPrincipalService must not be null");
        this.tokenVersionService = Objects.requireNonNull(
                tokenVersionService, "tokenVersionService must not be null");
        this.ticketStore = Objects.requireNonNull(
                ticketStore, "ticketStore must not be null");
        this.secretHasher = Objects.requireNonNull(
                secretHasher, "secretHasher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.secureRandom = Objects.requireNonNull(
                secureRandom, "secureRandom must not be null");
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("websocket ticket TTL must be positive");
        }
        this.ttl = ttl;
    }

    @Override
    public LogWebSocketTicketResponse issue() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        requireLogPrincipal(principal);
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(ttl);
        String ticket = generateTicket();
        LogWebSocketTicketClaims claims = LogWebSocketTicketClaims.from(
                principal, issuedAt, expiresAt);
        try {
            ticketStore.save(hash(ticket), claims, ttl);
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException(
                    "日志 WebSocket 票据存储暂时不可用", exception);
        }
        return new LogWebSocketTicketResponse(
                issuedAt,
                expiresAt,
                List.of(
                        AuthenticatedWebSocketConfigurator.APPLICATION_PROTOCOL,
                        AuthenticatedWebSocketConfigurator.TICKET_PROTOCOL_PREFIX + ticket));
    }

    @Override
    public AuthPrincipal consume(String rawTicket) {
        if (rawTicket == null
                || rawTicket.isBlank()
                || !rawTicket.matches("[A-Za-z0-9_-]{40,128}")) {
            throw invalid();
        }
        final Optional<LogWebSocketTicketClaims> stored;
        try {
            stored = ticketStore.consume(hash(rawTicket));
        } catch (RuntimeException exception) {
            throw new AuthenticationServiceException(
                    "日志 WebSocket 票据存储暂时不可用", exception);
        }
        LogWebSocketTicketClaims claims = stored.orElseThrow(this::invalid);
        Instant now = clock.instant();
        if (claims.issuedAt().isAfter(now) || !claims.expiresAt().isAfter(now)) {
            throw new CredentialsExpiredException("日志 WebSocket 票据已失效");
        }
        if (!tokenVersionService.matches(claims.userId(), claims.tokenVersion())) {
            throw new CredentialsExpiredException("日志 WebSocket 授权已变更");
        }
        AuthPrincipal principal = claims.toPrincipal();
        requireLogPrincipal(principal);
        return principal;
    }

    private void requireLogPrincipal(AuthPrincipal principal) {
        if (principal.type() != PrincipalType.USER
                || principal.userId() == null
                || principal.userId() <= 0
                || principal.id() <= 0
                || (!principal.systemUser()
                && !principal.permissions().contains(LOG_VIEW_PERMISSION))) {
            throw new AccessDeniedException("当前主体不能读取实时日志");
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

    private BadCredentialsException invalid() {
        return new BadCredentialsException("日志 WebSocket 票据无效");
    }
}
