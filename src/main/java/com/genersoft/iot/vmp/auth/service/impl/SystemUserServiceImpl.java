package com.genersoft.iot.vmp.auth.service.impl;

import com.genersoft.iot.vmp.auth.dto.CreateUserRequest;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.RoleAssignmentService;
import com.genersoft.iot.vmp.auth.service.SystemUserService;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.utils.DateUtil;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service
public class SystemUserServiceImpl implements SystemUserService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.@-]{1,64}$");

    private final IUserService userService;
    private final RoleAssignmentService roleAssignmentService;
    private final CurrentPrincipalService currentPrincipalService;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public SystemUserServiceImpl(
            IUserService userService,
            RoleAssignmentService roleAssignmentService,
            CurrentPrincipalService currentPrincipalService,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.roleAssignmentService = roleAssignmentService;
        this.currentPrincipalService = currentPrincipalService;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public long createUser(CreateUserRequest request) {
        validatePassword(request.password());
        if (!USERNAME.matcher(request.username()).matches()) {
            throw new IllegalArgumentException("用户名格式不合法");
        }
        TreeSet<Long> roleIds = normalizeRequiredRoles(request.roleIds());
        AuthPrincipal operator = currentPrincipalService.requireCurrent();

        User user = new User();
        user.setUsername(request.username().trim());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordVersion(1);
        user.setNickname(request.nickname());
        user.setDeptId(request.deptId());
        user.setPushKey(generatePushKey());
        user.setCreateBy(operator.userId());
        user.setUpdateBy(operator.userId());
        user.setCreateTime(DateUtil.getNow());
        user.setUpdateTime(DateUtil.getNow());
        Role compatibilityRole = new Role();
        compatibilityRole.setId(roleIds.first());
        user.setRole(compatibilityRole);
        if (userService.addUser(user) <= 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        roleAssignmentService.assignRoles(operator.userId(), user.getId(), roleIds);
        return user.getId();
    }

    @Override
    public void assignRoles(long userId, Set<Long> roleIds) {
        AuthPrincipal operator = currentPrincipalService.requireCurrent();
        roleAssignmentService.assignRoles(operator.userId(), userId, roleIds);
    }

    @Override
    @Transactional
    public void deleteUser(long userId) {
        AuthPrincipal operator = currentPrincipalService.requireCurrent();
        if (permissionRepository.isSystemUser(userId)) {
            throw new AccessDeniedException("系统管理员不能删除");
        }
        if (!userService.softDelete(userId, operator.userId())) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Override
    @Transactional
    public void resetPassword(long userId, String newPassword) {
        validatePassword(newPassword);
        assertCanManageProtectedUser(userId);
        User user = userService.getUserById(userId);
        if (user == null || user.isDeleted()) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!userService.upgradePassword(
                userId, user.getPassword(), passwordEncoder.encode(newPassword), 1)) {
            throw new IllegalStateException("密码重置失败");
        }
    }

    @Override
    @Transactional
    public void changePushKey(long userId, String pushKey) {
        assertCanManageProtectedUser(userId);
        if (userService.changePushKey(userId, pushKey) <= 0) {
            throw new IllegalArgumentException("用户不存在");
        }
    }

    @Override
    @Transactional
    public void changeOwnPassword(String oldPassword, String newPassword) {
        validatePassword(newPassword);
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        User user = userService.getUserById(principal.userId());
        String stored = adaptStoredPassword(user);
        if (user == null || !passwordEncoder.matches(oldPassword, stored)) {
            throw new BadCredentialsException("原密码错误");
        }
        if (!userService.upgradePassword(
                user.getId(), user.getPassword(), passwordEncoder.encode(newPassword), 1)) {
            throw new IllegalStateException("密码修改失败");
        }
    }

    private String adaptStoredPassword(User user) {
        if (user == null || user.getPassword() == null) {
            return "{bcrypt}$2a$10$00000000000000000000000000000000000000000000000000000";
        }
        if (user.getPasswordVersion() == 0 && user.getPassword().matches("[0-9a-fA-F]{32}")) {
            return "{legacy-md5}" + user.getPassword();
        }
        return user.getPassword();
    }

    private TreeSet<Long> normalizeRequiredRoles(Set<Long> roleIds) {
        TreeSet<Long> result = new TreeSet<>();
        if (roleIds != null) {
            roleIds.stream().filter(id -> id != null && id > 0).forEach(result::add);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("至少分配一个角色");
        }
        return result;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("密码长度必须为 8 到 128 个字符");
        }
    }

    private void assertCanManageProtectedUser(long userId) {
        AuthPrincipal operator = currentPrincipalService.requireCurrent();
        if (permissionRepository.isSystemUser(userId) && !operator.systemUser()) {
            throw new AccessDeniedException("普通管理员不能修改系统管理员");
        }
    }

    private String generatePushKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
