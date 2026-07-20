package com.genersoft.iot.vmp.auth.security;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.dto.LoginRequest;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.LoginAttemptService;
import com.genersoft.iot.vmp.auth.service.impl.UserAuthenticationServiceImpl;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordMigrationTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private IUserService userService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private LoginAttemptService loginAttemptService;
    @Mock
    private AuthorizationAuditSink auditSink;

    private PasswordEncoder passwordEncoder;
    private UserAuthenticationServiceImpl service;

    @BeforeEach
    void setUp() {
        passwordEncoder = new DelegatingPasswordEncoder("bcrypt", Map.of(
                "bcrypt", new BCryptPasswordEncoder(),
                "legacy-md5", new LegacyMd5PasswordEncoder()));
        UserSetting settings = new UserSetting();
        settings.setLoginTimeout(60);
        settings.setServerId("wvp-1");
        service = new UserAuthenticationServiceImpl(
                authenticationManager,
                userService,
                passwordEncoder,
                jwtUtils,
                settings,
                new AuthorizationProperties(AuthorizationMode.AUDIT, false, Duration.ofMinutes(5)),
                loginAttemptService,
                auditSink);
    }

    @Test
    void successfulRawPasswordLoginUpgradesLegacyMd5AndBumpsTokenVersion() {
        User legacy = user(12L, md5("plain-secret"), 0, 4L);
        User upgraded = user(12L, "{bcrypt}$2a$10$placeholder", 1, 5L);
        when(userService.getUserByUsername("operator")).thenReturn(legacy);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated("operator", null, java.util.List.of()));
        when(userService.upgradePassword(anyLong(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(userService.getUserById(12L)).thenReturn(upgraded);
        when(jwtUtils.createAccessToken(12L, 5L, Duration.ofMinutes(60))).thenReturn("token");
        when(jwtUtils.verifyAccessToken("token")).thenReturn(new VerifiedAccessToken(
                12L, "jti", 5L, Instant.parse("2026-07-13T10:00:00Z"),
                Instant.parse("2026-07-13T11:00:00Z")));

        assertEquals("token", service.login(
                new LoginRequest("operator", "plain-secret"), "127.0.0.1").accessToken());

        ArgumentCaptor<String> password = ArgumentCaptor.forClass(String.class);
        verify(userService).upgradePassword(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(md5("plain-secret")),
                password.capture(),
                org.mockito.ArgumentMatchers.eq(1));
        assertTrue(password.getValue().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("plain-secret", password.getValue()));
    }

    @Test
    void legacyEncoderUsesConstantValueComparisonSemantics() {
        LegacyMd5PasswordEncoder encoder = new LegacyMd5PasswordEncoder();
        assertTrue(encoder.matches("plain-secret", md5("plain-secret").toUpperCase()));
    }

    @Test
    void disabledLockedAndDeletedUsersShareOnePublicFailureButKeepAuditReasons() {
        User disabled = user(21L, md5("plain-secret"), 0, 0L);
        disabled.setStatus(1);
        User locked = user(22L, md5("plain-secret"), 0, 0L);
        locked.setLocked(true);
        User deleted = user(23L, md5("plain-secret"), 0, 0L);
        deleted.setDeleted(true);
        when(userService.getUserByUsername("disabled")).thenReturn(disabled);
        when(userService.getUserByUsername("locked")).thenReturn(locked);
        when(userService.getUserByUsername("deleted")).thenReturn(deleted);

        for (String username : List.of("disabled", "locked", "deleted")) {
            BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                    () -> service.login(new LoginRequest(username, "plain-secret"), "127.0.0.1"));
            assertEquals("用户名或密码错误", exception.getMessage());
        }

        ArgumentCaptor<AuthzAuditRecord> auditRecords = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink, times(3)).record(auditRecords.capture());
        assertEquals(
                List.of("LOGIN_ACCOUNT_DISABLED", "LOGIN_ACCOUNT_LOCKED", "LOGIN_ACCOUNT_DISABLED"),
                auditRecords.getAllValues().stream().map(AuthzAuditRecord::reasonCode).toList());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void rateLimitedLoginKeepsGenericResponseAndDedicatedAuditReason() {
        User user = user(12L, md5("plain-secret"), 0, 4L);
        when(userService.getUserByUsername("operator")).thenReturn(user);
        doThrow(new LockedException("用户名或密码错误"))
                .when(loginAttemptService).assertAllowed("operator", "127.0.0.1");

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> service.login(
                        new LoginRequest("operator", "plain-secret"), "127.0.0.1"));

        assertEquals("用户名或密码错误", exception.getMessage());
        ArgumentCaptor<AuthzAuditRecord> auditRecord = ArgumentCaptor.forClass(AuthzAuditRecord.class);
        verify(auditSink).record(auditRecord.capture());
        assertEquals("LOGIN_RATE_LIMITED", auditRecord.getValue().reasonCode());
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void legacyMd5LoginCannotOutliveConfiguredSunset() {
        service = new UserAuthenticationServiceImpl(
                authenticationManager,
                userService,
                passwordEncoder,
                jwtUtils,
                userSettings(),
                new AuthorizationProperties(
                        AuthorizationMode.AUDIT, false, Duration.ofMinutes(5),
                        true, "2000-01-01T00:00:00Z",
                        "0123456789abcdef0123456789abcdef", false,
                        "2026-10-01T00:00:00Z"),
                loginAttemptService,
                auditSink);

        BadCredentialsException exception = assertThrows(BadCredentialsException.class,
                () -> service.legacyMd5Login(
                        "operator", md5("plain-secret"), "127.0.0.1"));

        assertEquals("用户名或密码错误", exception.getMessage());
        verify(userService, never()).getUserByUsername(anyString());
    }

    private User user(long id, String password, int passwordVersion, long tokenVersion) {
        User user = new User();
        user.setId(id);
        user.setUsername("operator");
        user.setPassword(password);
        user.setPasswordVersion(passwordVersion);
        user.setTokenVersion(tokenVersion);
        return user;
    }

    private String md5(String value) {
        return DigestUtils.md5DigestAsHex(value.getBytes(StandardCharsets.UTF_8));
    }

    private UserSetting userSettings() {
        UserSetting settings = new UserSetting();
        settings.setLoginTimeout(60);
        settings.setServerId("wvp-1");
        return settings;
    }
}
