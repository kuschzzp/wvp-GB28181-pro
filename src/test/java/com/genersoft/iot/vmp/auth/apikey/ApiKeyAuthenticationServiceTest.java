package com.genersoft.iot.vmp.auth.apikey;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyAuthenticationServiceTest {

    @Mock private ApiKeySecretHasher secretHasher;
    @Mock private UserApiKeyMapper mapper;
    @Mock private IUserService userService;
    @Mock private PermissionRepository permissionRepository;
    @Mock private ApiKeyScopeService scopeService;

    private ApiKeyAuthenticationService service;

    @BeforeEach
    void setUp() {
        service = service(false, "2026-10-01T00:00:00Z");
    }

    @Test
    void authenticatesAsCredentialFreeMachinePrincipalWithKeyScopedPermissions() throws Exception {
        UserApiKey key = key();
        User owner = owner();
        stubHashedKey(key);
        stubOwner(owner, false);
        when(scopeService.resolvePermissions(eq(88L), argThat(principal ->
                principal.type() == PrincipalType.USER
                        && principal.userId() == 12L
                        && principal.tokenVersion() == 7L)))
                .thenReturn(Set.of("video:channel:list"));
        when(mapper.updateLastUsedAt(eq(88L), any())).thenReturn(1);

        AuthPrincipal principal = service.authenticate(
                "wvp_secret", InetAddress.getByName("127.0.0.1"));

        assertEquals(PrincipalType.API_KEY, principal.type());
        assertEquals(88L, principal.id());
        assertEquals(12L, principal.userId());
        assertEquals(Set.of("video:channel:list"), principal.permissions());
        assertFalse(principal.systemUser(), "API Key 不得继承系统用户绕过标志");
        verify(mapper).updateLastUsedAt(eq(88L), any());
    }

    @Test
    void rejectsExpiredDisabledAndRevokedKeys() throws Exception {
        UserApiKey key = key();
        stubHashedKey(key);

        key.setExpiredAt(System.currentTimeMillis() - 1);
        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));

        key.setExpiredAt(null);
        key.setStatus(ApiKeyStatus.DISABLED);
        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));

        key.setStatus(ApiKeyStatus.REVOKED);
        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));

        verify(userService, never()).getUserById(any(Long.class));
    }

    @Test
    void rejectsDisabledOwnerAndStaleAuthorizationVersion() throws Exception {
        UserApiKey key = key();
        stubHashedKey(key);
        User owner = owner();
        owner.setStatus(1);
        when(userService.getUserById(12L)).thenReturn(owner);

        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));

        owner.setStatus(0);
        key.setAuthzVersion(6L);
        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));
        verify(scopeService, never()).resolvePermissions(any(Long.class), any());
    }

    @Test
    void enforcesIpv4AndIpv6CidrAllowlistAgainstRemoteAddress() throws Exception {
        UserApiKey key = key();
        key.setIpAllowlist("10.0.0.0/8,2001:db8::/32");
        stubHashedKey(key);

        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate(
                        "wvp_secret", InetAddress.getByName("192.168.1.10")));
        verify(userService, never()).getUserById(any(Long.class));

        stubOwner(owner(), false);
        when(scopeService.resolvePermissions(eq(88L), any())).thenReturn(Set.of());
        when(mapper.updateLastUsedAt(eq(88L), any())).thenReturn(1);
        AuthPrincipal principal = service.authenticate(
                "wvp_secret", InetAddress.getByName("2001:db8::10"));
        assertEquals(88L, principal.id());
    }

    @Test
    void invalidStoredCidrAndPermissionDependencyFailuresFailClosed() throws Exception {
        UserApiKey key = key();
        key.setIpAllowlist("not-a-cidr");
        stubHashedKey(key);
        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));

        key.setIpAllowlist(null);
        stubOwner(owner(), false);
        when(scopeService.resolvePermissions(eq(88L), any()))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));
        verify(mapper, never()).updateLastUsedAt(eq(88L), any());
    }

    @Test
    void rejectsWhenLastUsedWriteShowsKeyWasDeletedConcurrently() throws Exception {
        UserApiKey key = key();
        stubHashedKey(key);
        stubOwner(owner(), false);
        when(scopeService.resolvePermissions(eq(88L), any())).thenReturn(Set.of());
        when(mapper.updateLastUsedAt(eq(88L), any())).thenReturn(0);

        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));
    }

    @Test
    void rejectsLegacyKeyAfterConfiguredSunset() throws Exception {
        service = service(true, "2000-01-01T00:00:00Z");
        UserApiKey key = key();
        key.setStatus(ApiKeyStatus.LEGACY);
        stubHashedKey(key);

        assertThrows(InvalidAccessTokenException.class,
                () -> service.authenticate("wvp_secret", InetAddress.getLoopbackAddress()));
    }

    @Test
    void acceptsLegacyZeroExpiryOnlyDuringCompatibilityWindowWithEmptyScope() throws Exception {
        service = service(true, "2099-01-01T00:00:00Z");
        UserApiKey key = key();
        key.setKeyHash(null);
        key.setLegacyApiKey("wvp_secret");
        key.setStatus(ApiKeyStatus.LEGACY);
        key.setExpiredAt(0L);
        when(secretHasher.hash("wvp_secret")).thenReturn("hash");
        when(mapper.selectByHash("hash")).thenReturn(null);
        when(mapper.selectLegacyByRawKey("wvp_secret")).thenReturn(key);
        stubOwner(owner(), false);
        when(scopeService.resolvePermissions(eq(88L), any())).thenReturn(Set.of());
        when(mapper.updateLastUsedAt(eq(88L), any())).thenReturn(1);

        AuthPrincipal principal = service.authenticate(
                "wvp_secret", InetAddress.getLoopbackAddress());

        assertEquals(PrincipalType.API_KEY, principal.type());
        assertEquals(88L, principal.id());
        assertEquals(Set.of(), principal.permissions());
    }

    private ApiKeyAuthenticationService service(boolean legacyEnabled, String legacySunset) {
        return new ApiKeyAuthenticationService(
                secretHasher,
                mapper,
                userService,
                permissionRepository,
                scopeService,
                new AuthorizationProperties(
                        AuthorizationMode.ENFORCE,
                        false,
                        Duration.ofMinutes(5),
                        false,
                        "2026-10-01T00:00:00Z",
                        "0123456789abcdef0123456789abcdef",
                        legacyEnabled,
                        legacySunset));
    }

    private void stubHashedKey(UserApiKey key) {
        when(secretHasher.hash("wvp_secret")).thenReturn("hash");
        when(mapper.selectByHash("hash")).thenReturn(key);
        when(secretHasher.matches("wvp_secret", "hash")).thenReturn(true);
    }

    private void stubOwner(User owner, boolean systemUser) {
        when(userService.getUserById(12L)).thenReturn(owner);
        when(permissionRepository.findEnabledRoleIds(12L)).thenReturn(List.of(3L));
        when(permissionRepository.isSystemUser(12L)).thenReturn(systemUser);
    }

    private UserApiKey key() {
        UserApiKey key = new UserApiKey();
        key.setId(88L);
        key.setUserId(12L);
        key.setKeyHash("hash");
        key.setStatus(ApiKeyStatus.ACTIVE);
        key.setAuthzVersion(7L);
        return key;
    }

    private User owner() {
        User owner = new User();
        owner.setId(12L);
        owner.setUsername("operator");
        owner.setStatus(0);
        owner.setTokenVersion(7L);
        return owner;
    }
}
