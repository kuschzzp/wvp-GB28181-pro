package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.service.impl.UserServiceImpl;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock private UserMapper userMapper;
    @Mock private UserRoleMapper userRoleMapper;
    @Mock private PermissionCache permissionCache;

    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userMapper, userRoleMapper, permissionCache);
    }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void successfulPasswordUpgradeEvictsOnlyAfterCommit() {
        when(userMapper.upgradePassword(
                eq(12L), eq("old-hash"), eq("new-hash"), eq(1), anyString()))
                .thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();

        assertTrue(service.upgradePassword(12L, "old-hash", "new-hash", 1));

        verify(permissionCache, never()).evictUser(12L);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(permissionCache).evictUser(12L);
    }

    @Test
    void successfulDeleteWithoutOuterTransactionEvictsImmediately() {
        when(userMapper.softDelete(
                eq(12L),
                eq(7L),
                anyString())).thenReturn(1);

        assertTrue(service.softDelete(12L, 7L));

        verify(permissionCache).evictUser(12L);
    }
}
