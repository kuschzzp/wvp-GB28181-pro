package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.impl.PermissionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTest {

    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    @Mock
    private PermissionRepository repository;

    @Mock
    private PermissionCache cache;

    private PermissionService service;

    @BeforeEach
    void setUp() {
        AuthorizationProperties properties = new AuthorizationProperties(
                AuthorizationMode.AUDIT, false, CACHE_TTL);
        service = new PermissionServiceImpl(repository, cache, properties);
    }

    @Test
    void resolvesEnabledPermissionsFromAllEnabledRoles() {
        when(cache.get(12L, 7L)).thenReturn(Optional.empty());
        when(repository.isSystemUser(12L)).thenReturn(false);
        when(repository.findPermissionCodes(12L)).thenReturn(List.of(
                "video:channel:list",
                "video:channel:play",
                "video:channel:play",
                " "));

        Set<String> permissions = service.resolvePermissions(12L, 7L);

        assertEquals(Set.of("video:channel:list", "video:channel:play"), permissions);
        verify(cache).put(12L, 7L, permissions, CACHE_TTL);
    }

    @Test
    void systemRoleGetsEveryEnabledPermission() {
        when(cache.get(1L, 3L)).thenReturn(Optional.empty());
        when(repository.isSystemUser(1L)).thenReturn(true);
        when(repository.findAllEnabledPermissionCodes()).thenReturn(List.of(
                "system:user:list", "video:channel:list"));

        assertEquals(Set.of("system:user:list", "video:channel:list"),
                service.resolvePermissions(1L, 3L));

        verify(repository, never()).findPermissionCodes(anyLong());
    }

    @Test
    void usesTokenVersionedCacheWithoutQueryingDatabase() {
        Set<String> cached = Set.of("video:channel:list");
        when(cache.get(12L, 9L)).thenReturn(Optional.of(cached));

        assertEquals(cached, service.resolvePermissions(12L, 9L));

        verifyNoInteractions(repository);
        verify(cache, never()).put(eq(12L), eq(9L), eq(cached), eq(CACHE_TTL));
    }

    @Test
    void cacheFailureIsNotConvertedToAnAllowDecision() {
        when(cache.get(12L, 7L)).thenThrow(new IllegalStateException("redis unavailable"));

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> service.resolvePermissions(12L, 7L));

        assertEquals("redis unavailable", exception.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void grantingRoleIdsArePermissionSpecificAndDeduplicated() {
        when(repository.findGrantingRoleIds(12L, "video:channel:play"))
                .thenReturn(List.of(7L, 8L, 7L));

        assertEquals(Set.of(7L, 8L),
                service.findGrantingRoleIds(12L, "video:channel:play"));
    }

    @Test
    void principalPermissionsAreTheOnlyFunctionalGrantSource() {
        AuthPrincipal principal = new AuthPrincipal(
                PrincipalType.USER,
                12L,
                12L,
                "operator",
                5L,
                Set.of(7L),
                2L,
                false,
                Set.of("video:channel:list"));

        assertTrue(service.hasPermission(principal, "video:channel:list"));
        assertFalse(service.hasPermission(principal, "video:channel:play"));
    }
}
