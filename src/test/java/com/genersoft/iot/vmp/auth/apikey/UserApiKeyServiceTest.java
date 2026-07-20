package com.genersoft.iot.vmp.auth.apikey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyCreatedResponse;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeySummary;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeCommand;
import com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyScopeSnapshot;
import com.genersoft.iot.vmp.auth.apikey.dto.CreateApiKeyCommand;
import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.service.impl.UserApiKeyServiceImpl;
import com.genersoft.iot.vmp.storager.dao.UserApiKeyMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApiKeyServiceTest {

    @Mock private UserApiKeyMapper mapper;
    @Mock private IUserService userService;
    @Mock private ApiKeySecretHasher secretHasher;
    @Mock private CurrentPrincipalService currentPrincipalService;
    @Mock private DepartmentCommandPolicy commandPolicy;
    @Mock private ApiKeyScopeService scopeService;

    private UserApiKeyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserApiKeyServiceImpl(
                mapper, userService, secretHasher, currentPrincipalService, commandPolicy,
                scopeService);
    }

    @Test
    void persistsOnlyHashAndReturnsRawSecretExactlyInCreationDto() throws Exception {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));
        User owner = new User();
        owner.setId(12L);
        owner.setTokenVersion(7L);
        when(userService.getUserById(12L)).thenReturn(owner);
        when(secretHasher.generate()).thenReturn(new GeneratedApiKey(
                "wvp_raw-secret", "wvp_raw-secr", "cret", "server-hmac"));
        doAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            key.setId(88L);
            return 1;
        }).when(mapper).add(org.mockito.ArgumentMatchers.any(UserApiKey.class));
        Instant expiresAt = Instant.parse("2027-01-01T00:00:00Z");

        ApiKeyCreatedResponse response = service.create(
                12L,
                new CreateApiKeyCommand("nvr", "integration", expiresAt, true),
                ownScope());

        ArgumentCaptor<UserApiKey> persisted = ArgumentCaptor.forClass(UserApiKey.class);
        org.mockito.Mockito.verify(mapper).add(persisted.capture());
        assertEquals("server-hmac", persisted.getValue().getKeyHash());
        assertEquals(expiresAt.toEpochMilli(), persisted.getValue().getExpiredAt());
        assertEquals("wvp_raw-secret", response.secret());
        org.mockito.Mockito.verify(scopeService).replaceScope(
                org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.same(owner),
                org.mockito.ArgumentMatchers.eq(ApiKeyScopeCommand.empty()));
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        assertFalse(objectMapper.writeValueAsString(persisted.getValue()).contains("server-hmac"));
        assertFalse(objectMapper.writeValueAsString(ApiKeySummary.from(persisted.getValue()))
                .contains("wvp_raw-secret"));
    }

    @Test
    void cannotUseArbitraryOwnerIdWithoutManageOthersPermission() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));

        assertThrows(AccessDeniedException.class,
                () -> service.create(
                        99L,
                        new CreateApiKeyCommand("nvr", null, null, true),
                        DepartmentScope.all(12L)));

        verifyNoInteractions(userService, secretHasher, mapper);
    }

    @Test
    void serviceValidationAlsoProtectsLegacyQueryAndRemarkEndpoints() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));

        assertThrows(IllegalArgumentException.class,
                () -> service.create(12L, new CreateApiKeyCommand(
                        "a".repeat(256), null, null, true), ownScope()));

        UserApiKey key = new UserApiKey();
        key.setId(88L);
        key.setUserId(12L);
        when(mapper.selectById(88L)).thenReturn(key);
        assertThrows(IllegalArgumentException.class,
                () -> service.updateRemark(12L, 88L, "r".repeat(256), ownScope()));

        verifyNoInteractions(userService, secretHasher);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).add(
                org.mockito.ArgumentMatchers.any(UserApiKey.class));
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never()).updateRemark(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rotatingLegacyNeverExpireKeyNormalizesZeroToNull() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));
        User owner = new User();
        owner.setId(12L);
        owner.setTokenVersion(7L);
        when(userService.getUserById(12L)).thenReturn(owner);
        UserApiKey legacy = new UserApiKey();
        legacy.setId(88L);
        legacy.setUserId(12L);
        legacy.setLegacyApiKey("legacy-secret");
        legacy.setExpiredAt(0L);
        legacy.setStatus(ApiKeyStatus.LEGACY);
        when(mapper.selectById(88L)).thenReturn(legacy);
        when(secretHasher.generate()).thenReturn(new GeneratedApiKey(
                "wvp_new-secret", "wvp_new-secr", "cret", "new-hmac"));
        when(mapper.rotate(org.mockito.ArgumentMatchers.any(UserApiKey.class))).thenReturn(1);

        ApiKeyCreatedResponse response = service.rotate(12L, 88L, ownScope());

        ArgumentCaptor<UserApiKey> rotated = ArgumentCaptor.forClass(UserApiKey.class);
        org.mockito.Mockito.verify(mapper).rotate(rotated.capture());
        org.mockito.Mockito.verify(scopeService).validateStoredScope(legacy, owner);
        assertNull(rotated.getValue().getExpiredAt());
        assertEquals(ApiKeyStatus.ACTIVE, rotated.getValue().getStatus());
        assertNull(rotated.getValue().getLegacyApiKey());
        assertEquals("wvp_new-secret", response.secret());
    }

    @Test
    void activationRejectsLegacyExpiredAndStaleKeysUntilTheyAreRotated() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));
        User owner = new User();
        owner.setId(12L);
        owner.setTokenVersion(7L);
        when(userService.getUserById(12L)).thenReturn(owner);
        UserApiKey key = new UserApiKey();
        key.setId(88L);
        key.setUserId(12L);
        key.setStatus(ApiKeyStatus.DISABLED);
        key.setAuthzVersion(7L);
        when(mapper.selectById(88L)).thenReturn(key);

        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(12L, 88L, ApiKeyStatus.ACTIVE, ownScope()));

        key.setKeyHash("server-hmac");
        key.setAuthzVersion(6L);
        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(12L, 88L, ApiKeyStatus.ACTIVE, ownScope()));

        key.setAuthzVersion(7L);
        key.setExpiredAt(System.currentTimeMillis() - 1);
        assertThrows(IllegalArgumentException.class,
                () -> service.changeStatus(12L, 88L, ApiKeyStatus.ACTIVE, ownScope()));

        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.never())
                .updateStatus(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void activationUpdatesAUsableCurrentKey() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));
        User owner = new User();
        owner.setId(12L);
        owner.setTokenVersion(7L);
        when(userService.getUserById(12L)).thenReturn(owner);
        UserApiKey key = new UserApiKey();
        key.setId(88L);
        key.setUserId(12L);
        key.setKeyHash("server-hmac");
        key.setStatus(ApiKeyStatus.DISABLED);
        key.setAuthzVersion(7L);
        when(mapper.selectById(88L)).thenReturn(key);
        when(mapper.updateStatus(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.eq(ApiKeyStatus.ACTIVE),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(1);

        service.changeStatus(12L, 88L, ApiKeyStatus.ACTIVE, ownScope());

        org.mockito.Mockito.verify(mapper).updateStatus(
                org.mockito.ArgumentMatchers.eq(12L),
                org.mockito.ArgumentMatchers.eq(88L),
                org.mockito.ArgumentMatchers.eq(ApiKeyStatus.ACTIVE),
                org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.verify(scopeService).validateStoredScope(key, owner);
    }

    @Test
    void createAndUpdateScopeUseTheOwnersCurrentValidatedSubset() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));
        User owner = new User();
        owner.setId(12L);
        owner.setTokenVersion(7L);
        when(userService.getUserById(12L)).thenReturn(owner);
        when(secretHasher.generate()).thenReturn(new GeneratedApiKey(
                "wvp_raw-secret", "wvp_raw-secr", "cret", "server-hmac"));
        doAnswer(invocation -> {
            UserApiKey key = invocation.getArgument(0);
            key.setId(88L);
            return 1;
        }).when(mapper).add(org.mockito.ArgumentMatchers.any(UserApiKey.class));
        ApiKeyScopeCommand selected = new ApiKeyScopeCommand(
                Set.of(501L), Set.of(601L), java.util.List.of("10.0.0.0/8"));

        service.create(
                12L,
                new CreateApiKeyCommand(
                        "nvr", null, null, true,
                        selected.permissionMenuIds(),
                        selected.resourceGroupIds(),
                        selected.ipAllowlist()),
                ownScope());

        UserApiKey persisted = new UserApiKey();
        persisted.setId(88L);
        persisted.setUserId(12L);
        when(mapper.selectById(88L)).thenReturn(persisted);
        service.updateScope(12L, 88L, selected, ownScope());

        org.mockito.Mockito.verify(scopeService, org.mockito.Mockito.times(2))
                .replaceScope(88L, owner, selected);
    }

    @Test
    void listIncludesPermissionResourceAndIpScopeWithoutSecrets() {
        when(currentPrincipalService.requireCurrent()).thenReturn(principal(12L, Set.of()));
        UserApiKey key = new UserApiKey();
        key.setId(88L);
        key.setUserId(12L);
        key.setKeyPrefix("wvp_abcd");
        key.setKeyLast4("1234");
        when(mapper.findByOwnerId(12L)).thenReturn(java.util.List.of(key));
        when(scopeService.snapshot(key)).thenReturn(new ApiKeyScopeSnapshot(
                Set.of(501L), Set.of(601L), java.util.List.of("10.0.0.0/8")));

        ApiKeySummary summary = service.pageOwnKeys(
                12L, new com.genersoft.iot.vmp.auth.apikey.dto.ApiKeyQuery(1, 20), ownScope())
                .getList().getFirst();

        assertEquals(Set.of(501L), summary.permissionMenuIds());
        assertEquals(Set.of(601L), summary.resourceGroupIds());
        assertEquals(java.util.List.of("10.0.0.0/8"), summary.ipAllowlist());
    }

    private AuthPrincipal principal(long userId, Set<String> permissions) {
        return new AuthPrincipal(
                PrincipalType.USER, userId, userId, "operator", null,
                Set.of(3L), 7L, false, permissions);
    }

    private DepartmentScope ownScope() {
        return DepartmentScope.self(12L);
    }
}
