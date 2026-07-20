package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.auth.service.impl.TokenVersionServiceImpl;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenVersionServiceTest {

    @Mock private UserMapper userMapper;
    @Mock private PermissionCache permissionCache;

    private TokenVersionService service;

    @BeforeEach
    void setUp() {
        service = new TokenVersionServiceImpl(userMapper, permissionCache);
    }

    @Test
    void matchesOnlyCurrentActiveUserVersion() {
        when(userMapper.findActiveTokenVersion(12L)).thenReturn(7L);
        assertTrue(service.matches(12L, 7L));
        assertFalse(service.matches(12L, 6L));
    }

    @Test
    void roleChangeBumpsAffectedUsersBeforeEvictingTheirPermissionCache() {
        when(userMapper.findUserIdsByRoleId(3L)).thenReturn(List.of(12L, 13L));
        when(userMapper.bumpTokenVersions(eq(java.util.Set.of(12L, 13L)), anyString())).thenReturn(2);

        service.bumpUsersByRoleId(3L, "ROLE_PERMISSION_CHANGED");

        verify(permissionCache).evictUser(12L);
        verify(permissionCache).evictUser(13L);
    }
}
