package com.genersoft.iot.vmp.auth.repository.impl;

import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.storager.dao.PermissionQueryMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MyBatisPermissionRepository implements PermissionRepository {

    private final PermissionQueryMapper mapper;

    public MyBatisPermissionRepository(PermissionQueryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean isSystemUser(long userId) {
        return mapper.countSystemRoles(userId) > 0;
    }

    @Override
    public List<String> findPermissionCodes(long userId) {
        return mapper.findPermissionCodes(userId);
    }

    @Override
    public List<String> findAllEnabledPermissionCodes() {
        return mapper.findAllEnabledPermissionCodes();
    }

    @Override
    public List<Long> findEnabledRoleIds(long userId) {
        return mapper.findEnabledRoleIds(userId);
    }

    @Override
    public List<Long> findGrantingRoleIds(long userId, String permissionCode) {
        return mapper.findGrantingRoleIds(userId, permissionCode);
    }
}
