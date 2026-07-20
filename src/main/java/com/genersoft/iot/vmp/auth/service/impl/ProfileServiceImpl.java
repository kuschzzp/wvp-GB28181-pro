package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.dto.ProfileResponse;
import com.genersoft.iot.vmp.auth.dto.UserSummary;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.ProfileService;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.RoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

@Service
public class ProfileServiceImpl implements ProfileService {

    private final CurrentPrincipalService currentPrincipalService;
    private final IUserService userService;
    private final RoleMapper roleMapper;

    public ProfileServiceImpl(
            CurrentPrincipalService currentPrincipalService,
            IUserService userService,
            RoleMapper roleMapper) {
        this.currentPrincipalService = currentPrincipalService;
        this.userService = userService;
        this.roleMapper = roleMapper;
    }

    @Override
    public ProfileResponse currentProfile() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        User user = userService.getUserById(principal.userId());
        if (user == null || user.isDeleted() || user.getStatus() != 0 || user.isLocked()) {
            throw new IllegalStateException("当前用户状态无效");
        }
        TreeSet<String> roleKeys = new TreeSet<>();
        if (!principal.roleIds().isEmpty()) {
            roleMapper.findByIds(principal.roleIds()).stream()
                    .map(Role::getRoleKey)
                    .filter(roleKey -> roleKey != null && !roleKey.isBlank())
                    .forEach(roleKeys::add);
        }
        return new ProfileResponse(
                UserSummary.from(user),
                Collections.unmodifiableSet(roleKeys),
                Set.copyOf(principal.permissions()),
                principal.tokenVersion(),
                principal.systemUser());
    }
}
