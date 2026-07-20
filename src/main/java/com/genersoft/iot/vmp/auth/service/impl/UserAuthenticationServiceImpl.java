package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.dto.LoginRequest;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;
import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.service.LoginAttemptService;
import com.genersoft.iot.vmp.auth.service.UserAuthenticationService;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class UserAuthenticationServiceImpl implements UserAuthenticationService {

    private static final int LEGACY_PASSWORD_VERSION = 0;
    private static final int BCRYPT_PASSWORD_VERSION = 1;
    private static final Pattern LEGACY_MD5 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final String LOGIN_PERMISSION = "auth:user:login";

    private final AuthenticationManager authenticationManager;
    private final IUserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserSetting userSetting;
    private final AuthorizationProperties authorizationProperties;
    private final LoginAttemptService loginAttemptService;
    private final AuthorizationAuditSink auditSink;

    public UserAuthenticationServiceImpl(
            AuthenticationManager authenticationManager,
            IUserService userService,
            PasswordEncoder passwordEncoder,
            JwtUtils jwtUtils,
            UserSetting userSetting,
            AuthorizationProperties authorizationProperties,
            LoginAttemptService loginAttemptService,
            AuthorizationAuditSink auditSink) {
        this.authenticationManager = authenticationManager;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtils = jwtUtils;
        this.userSetting = userSetting;
        this.authorizationProperties = authorizationProperties;
        this.loginAttemptService = loginAttemptService;
        this.auditSink = auditSink;
    }

    @Override
    public LoginResponse login(LoginRequest request, String clientIp) {
        String username = request.username().trim();
        User storedUser = userService.getUserByUsername(username);
        assertAttemptAllowed(username, clientIp, storedUser);
        try {
            assertActive(storedUser);
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(username, request.password()));
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(username, clientIp);
            audit(storedUser, clientIp, false, reasonFor(exception));
            throw new BadCredentialsException("用户名或密码错误");
        }

        User tokenUser = migrateLegacyPassword(storedUser, request.password());
        loginAttemptService.clear(username, clientIp);
        userService.updateLastLogin(tokenUser.getId(), LocalDateTime.now());
        audit(tokenUser, clientIp, true, "LOGIN_SUCCESS");
        return issueToken(tokenUser);
    }

    @Override
    public LoginResponse legacyMd5Login(String username, String clientMd5, String clientIp) {
        if (!authorizationProperties.legacyMd5LoginEnabled()
                || !Instant.now().isBefore(
                Instant.parse(authorizationProperties.legacyMd5LoginSunset()))) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        String normalizedUsername = username == null ? "" : username.trim();
        User user = userService.getUserByUsername(normalizedUsername);
        assertAttemptAllowed(normalizedUsername, clientIp, user);
        try {
            assertActive(user);
            if (user.getPasswordVersion() != LEGACY_PASSWORD_VERSION
                    || !isLegacyMd5(user.getPassword())
                    || !isLegacyMd5(clientMd5)
                    || !constantTimeEquals(clientMd5, user.getPassword())) {
                throw new BadCredentialsException("用户名或密码错误");
            }
        } catch (AuthenticationException exception) {
            loginAttemptService.recordFailure(normalizedUsername, clientIp);
            audit(user, clientIp, false, reasonFor(exception));
            throw new BadCredentialsException("用户名或密码错误");
        }

        loginAttemptService.clear(normalizedUsername, clientIp);
        userService.updateLastLogin(user.getId(), LocalDateTime.now());
        audit(user, clientIp, true, "LEGACY_LOGIN_SUCCESS");
        return issueToken(user);
    }

    private User migrateLegacyPassword(User storedUser, String rawPassword) {
        if (storedUser.getPasswordVersion() != LEGACY_PASSWORD_VERSION
                || !isLegacyMd5(storedUser.getPassword())) {
            return storedUser;
        }
        String upgradedPassword = passwordEncoder.encode(rawPassword);
        boolean upgraded = userService.upgradePassword(
                storedUser.getId(),
                storedUser.getPassword(),
                upgradedPassword,
                BCRYPT_PASSWORD_VERSION);
        User current = userService.getUserById(storedUser.getId());
        if (current == null || (!upgraded && !passwordEncoder.matches(rawPassword, current.getPassword()))) {
            throw new AuthenticationServiceException("密码升级失败，请重新登录");
        }
        return current;
    }

    private LoginResponse issueToken(User user) {
        Duration ttl = Duration.ofMinutes(userSetting.getLoginTimeout());
        String accessToken = jwtUtils.createAccessToken(user.getId(), user.getTokenVersion(), ttl);
        VerifiedAccessToken verified = jwtUtils.verifyAccessToken(accessToken);
        return new LoginResponse(accessToken, verified.expiresAt(), userSetting.getServerId());
    }

    private void assertActive(User user) {
        if (user == null) {
            throw new BadCredentialsException("用户名或密码错误");
        }
        if (user.isDeleted() || user.getStatus() != 0) {
            throw new DisabledException("用户名或密码错误");
        }
        if (user.isLocked()) {
            throw new LockedException("用户名或密码错误");
        }
    }

    private void audit(User user, String clientIp, boolean allowed, String reasonCode) {
        auditSink.record(new AuthzAuditRecord(
                PrincipalType.USER,
                user == null ? 0 : user.getId(),
                user == null ? null : user.getDeptId(),
                LOGIN_PERMISSION,
                null,
                null,
                allowed,
                reasonCode,
                null,
                clientIp,
                LocalDateTime.now()));
    }

    private String reasonFor(AuthenticationException exception) {
        return exception instanceof LockedException
                ? "LOGIN_ACCOUNT_LOCKED"
                : exception instanceof DisabledException
                ? "LOGIN_ACCOUNT_DISABLED"
                : "LOGIN_BAD_CREDENTIALS";
    }

    private void assertAttemptAllowed(String username, String clientIp, User user) {
        try {
            loginAttemptService.assertAllowed(username, clientIp);
        } catch (LockedException exception) {
            audit(user, clientIp, false, "LOGIN_RATE_LIMITED");
            throw new BadCredentialsException("用户名或密码错误");
        }
    }

    private boolean isLegacyMd5(String value) {
        return value != null && LEGACY_MD5.matcher(value).matches();
    }

    private boolean constantTimeEquals(String actual, String expected) {
        return MessageDigest.isEqual(
                actual.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII),
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}
