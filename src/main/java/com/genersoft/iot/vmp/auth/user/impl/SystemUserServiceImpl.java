package com.genersoft.iot.vmp.auth.user.impl;

import com.genersoft.iot.vmp.auth.department.DepartmentCommandPolicy;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.dto.CreateUserRequest;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.RoleAssignmentService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.auth.user.SystemUserService;
import com.genersoft.iot.vmp.auth.user.dto.AdminUpdateUserRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeUserStatusRequest;
import com.genersoft.iot.vmp.auth.user.dto.ChangeOwnPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.ResetPasswordRequest;
import com.genersoft.iot.vmp.auth.user.dto.UpdateOwnProfileRequest;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.auth.user.dto.UserRoleRef;
import com.genersoft.iot.vmp.auth.user.dto.UserSummary;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.Role;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.utils.DateUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

@Service("scopedSystemUserService")
public class SystemUserServiceImpl implements SystemUserService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.@-]{1,64}$");

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final DepartmentCommandPolicy commandPolicy;
    private final CurrentPrincipalService currentPrincipalService;
    private final PermissionRepository permissionRepository;
    private final PasswordEncoder passwordEncoder;
    private final IUserService legacyUserService;
    private final RoleAssignmentService roleAssignmentService;
    private final TokenVersionService tokenVersionService;
    private final SecureRandom secureRandom = new SecureRandom();

    public SystemUserServiceImpl(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            DepartmentCommandPolicy commandPolicy,
            CurrentPrincipalService currentPrincipalService,
            PermissionRepository permissionRepository,
            PasswordEncoder passwordEncoder,
            IUserService legacyUserService,
            RoleAssignmentService roleAssignmentService,
            TokenVersionService tokenVersionService) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.commandPolicy = commandPolicy;
        this.currentPrincipalService = currentPrincipalService;
        this.permissionRepository = permissionRepository;
        this.passwordEncoder = passwordEncoder;
        this.legacyUserService = legacyUserService;
        this.roleAssignmentService = roleAssignmentService;
        this.tokenVersionService = tokenVersionService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageInfo<UserSummary> query(UserQuery query, DepartmentScope scope) {
        Objects.requireNonNull(query, "query must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        PageHelper.startPage(query.page(), query.count());
        List<UserSummary> users = userMapper.queryScoped(query, scope);
        PageInfo<UserSummary> page = new PageInfo<>(users);
        attachRoles(page.getList());
        return page;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findVisible(long userId, DepartmentScope scope) {
        if (userId <= 0) {
            return Optional.empty();
        }
        Objects.requireNonNull(scope, "scope must not be null");
        UserSummary user = userMapper.findVisibleScoped(userId, scope);
        if (user == null) {
            return Optional.empty();
        }
        attachRoles(List.of(user));
        return Optional.of(user);
    }

    @Override
    @Transactional
    public long create(CreateUserRequest request, DepartmentScope scope) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        AuthPrincipal operator = requireUserPrincipal();
        String username = normalizeUsername(request.username());
        validatePassword(request.password());
        if (request.deptId() == null) {
            throw new IllegalArgumentException("主部门不能为空");
        }
        TreeSet<Long> roleIds = normalizeRequiredRoleIds(request.roleIds());
        commandPolicy.requireDepartmentInScope(request.deptId(), scope);
        commandPolicy.requireRoleAssignmentAllowed(operator, roleIds);

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setPasswordVersion(1);
        user.setNickname(normalizeNickname(request.nickname()));
        user.setDeptId(request.deptId());
        user.setPushKey(generatePushKey());
        user.setCreateBy(operator.userId());
        user.setUpdateBy(operator.userId());
        user.setCreateTime(DateUtil.getNow());
        user.setUpdateTime(DateUtil.getNow());
        Role compatibilityRole = new Role();
        compatibilityRole.setId(roleIds.first());
        user.setRole(compatibilityRole);
        if (legacyUserService.addUser(user) <= 0) {
            throw new IllegalArgumentException("用户名已存在");
        }
        roleAssignmentService.assignRoles(operator.userId(), user.getId(), roleIds);
        return user.getId();
    }

    @Override
    @Transactional
    public void update(long userId, AdminUpdateUserRequest request, DepartmentScope scope) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        AuthPrincipal operator = requireUserPrincipal();
        commandPolicy.requireUserInScope(userId, scope);
        requireNotSelfAdministrativeMutation(operator, userId);
        requireManageableUser(operator, userId);
        commandPolicy.requireDepartmentInScope(request.deptId(), scope);
        User current = requireExistingUser(userId);

        int changed = userMapper.updateAdminProfile(
                userId,
                normalizeRequiredNickname(request.nickname()),
                request.deptId(),
                operator.userId(),
                DateUtil.getNow());
        if (changed != 1) {
            throw notFound("用户不存在");
        }
        if (!Objects.equals(current.getDeptId(), request.deptId())) {
            tokenVersionService.bumpUser(userId, "USER_DEPARTMENT_CHANGED");
        }
    }

    @Override
    @Transactional
    public void changeStatus(
            long userId,
            ChangeUserStatusRequest request,
            DepartmentScope scope) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        if (request.status() != 0 && request.status() != 1) {
            throw new IllegalArgumentException("用户状态不合法");
        }
        AuthPrincipal operator = requireUserPrincipal();
        commandPolicy.requireUserInScope(userId, scope);
        requireNotSelfAdministrativeMutation(operator, userId);
        requireManageableUser(operator, userId);
        User current = requireExistingUser(userId);
        if (current.getStatus() == request.status() && current.isLocked() == request.locked()) {
            return;
        }
        int changed = userMapper.updateAccountStatus(
                userId,
                request.status(),
                request.locked(),
                operator.userId(),
                DateUtil.getNow());
        if (changed != 1) {
            throw notFound("用户不存在");
        }
        tokenVersionService.bumpUser(userId, "USER_STATUS_CHANGED");
    }

    @Override
    @Transactional
    public void assignRoles(long userId, Set<Long> roleIds, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        AuthPrincipal operator = requireUserPrincipal();
        commandPolicy.requireUserInScope(userId, scope);
        requireNotSelfAdministrativeMutation(operator, userId);
        requireManageableUser(operator, userId);
        TreeSet<Long> normalizedRoleIds = normalizeRequiredRoleIds(roleIds);
        commandPolicy.requireRoleAssignmentAllowed(operator, normalizedRoleIds);
        roleAssignmentService.assignRoles(operator.userId(), userId, normalizedRoleIds);
    }

    @Override
    @Transactional
    public void delete(long userId, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        AuthPrincipal operator = requireUserPrincipal();
        commandPolicy.requireUserInScope(userId, scope);
        requireNotSelfAdministrativeMutation(operator, userId);
        requireManageableUser(operator, userId);
        if (!legacyUserService.softDelete(userId, operator.userId())) {
            throw notFound("用户不存在");
        }
    }

    @Override
    @Transactional
    public void resetPassword(
            long userId,
            ResetPasswordRequest request,
            DepartmentScope scope) {
        Objects.requireNonNull(request, "request must not be null");
        Objects.requireNonNull(scope, "scope must not be null");
        validatePassword(request.password());
        AuthPrincipal operator = requireUserPrincipal();
        commandPolicy.requireUserInScope(userId, scope);
        requireManageableUser(operator, userId);
        User user = legacyUserService.getUserById(userId);
        if (user == null || user.isDeleted()) {
            throw notFound("用户不存在");
        }
        if (!legacyUserService.upgradePassword(
                userId,
                user.getPassword(),
                passwordEncoder.encode(request.password()),
                1)) {
            throw new IllegalStateException("密码重置失败");
        }
    }

    @Override
    @Transactional
    public void changePushKey(long userId, String pushKey, DepartmentScope scope) {
        Objects.requireNonNull(scope, "scope must not be null");
        AuthPrincipal operator = requireUserPrincipal();
        commandPolicy.requireUserInScope(userId, scope);
        requireNotSelfAdministrativeMutation(operator, userId);
        requireManageableUser(operator, userId);
        String normalizedPushKey = normalizeRequiredPushKey(pushKey);
        if (legacyUserService.changePushKey(userId, normalizedPushKey) != 1) {
            throw notFound("用户不存在");
        }
    }

    @Override
    @Transactional
    public void updateOwnProfile(UpdateOwnProfileRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        AuthPrincipal principal = requireUserPrincipal();
        if (userMapper.updateOwnProfile(
                principal.userId(),
                normalizeRequiredNickname(request.nickname()),
                principal.userId(),
                DateUtil.getNow()) != 1) {
            throw notFound("用户不存在");
        }
    }

    @Override
    @Transactional
    public void changeOwnPassword(ChangeOwnPasswordRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        validatePassword(request.newPassword());
        AuthPrincipal principal = requireUserPrincipal();
        User user = legacyUserService.getUserById(principal.userId());
        String storedPassword = adaptStoredPassword(user);
        if (user == null || !passwordEncoder.matches(request.oldPassword(), storedPassword)) {
            throw new BadCredentialsException("原密码错误");
        }
        if (!legacyUserService.upgradePassword(
                user.getId(),
                user.getPassword(),
                passwordEncoder.encode(request.newPassword()),
                1)) {
            throw new IllegalStateException("密码修改失败");
        }
    }

    private void attachRoles(List<UserSummary> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<Long, Set<Long>> roleIdsByUser = new LinkedHashMap<>();
        for (UserRoleRef ref : userRoleMapper.findRoleRefsByUserIds(
                users.stream().map(UserSummary::getId).toList())) {
            roleIdsByUser.computeIfAbsent(ref.userId(), ignored -> new LinkedHashSet<>())
                    .add(ref.roleId());
        }
        for (UserSummary user : users) {
            user.setRoleIds(roleIdsByUser.getOrDefault(user.getId(), Set.of()));
        }
    }

    private AuthPrincipal requireUserPrincipal() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        if (principal.userId() == null || principal.userId() <= 0) {
            throw new AccessDeniedException("当前主体不是有效用户");
        }
        return principal;
    }

    private void requireNotSelfAdministrativeMutation(AuthPrincipal operator, long targetUserId) {
        if (operator.userId() == targetUserId) {
            throw new AccessDeniedException("不能通过管理接口修改本人角色、部门、状态或删除本人");
        }
    }

    private void requireManageableUser(AuthPrincipal operator, long targetUserId) {
        if (permissionRepository.isSystemUser(targetUserId) && !operator.systemUser()) {
            throw new AccessDeniedException("普通管理员不能操作系统管理员");
        }
    }

    private User requireExistingUser(long userId) {
        User user = userMapper.selectById(userId);
        if (user == null || user.isDeleted()) {
            throw notFound("用户不存在");
        }
        return user;
    }

    private TreeSet<Long> normalizeRequiredRoleIds(Set<Long> roleIds) {
        TreeSet<Long> normalized = new TreeSet<>();
        if (roleIds != null) {
            roleIds.stream()
                    .filter(Objects::nonNull)
                    .filter(roleId -> roleId > 0)
                    .forEach(normalized::add);
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("至少分配一个角色");
        }
        return normalized;
    }

    private String normalizeUsername(String username) {
        if (username == null || !USERNAME.matcher(username.trim()).matches()) {
            throw new IllegalArgumentException("用户名格式不合法");
        }
        return username.trim();
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null) {
            return null;
        }
        String normalized = nickname.trim();
        if (normalized.length() > 64) {
            throw new IllegalArgumentException("昵称长度不能超过 64 个字符");
        }
        return normalized;
    }

    private String normalizeRequiredNickname(String nickname) {
        String normalized = normalizeNickname(nickname);
        if (normalized == null || normalized.isBlank()) {
            throw new IllegalArgumentException("昵称不能为空");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("密码长度必须为 8 到 128 个字符");
        }
    }

    private String normalizeRequiredPushKey(String pushKey) {
        if (pushKey == null || pushKey.isBlank()) {
            throw new IllegalArgumentException("推流密钥不能为空");
        }
        String normalized = pushKey.trim();
        if (normalized.length() > 50) {
            throw new IllegalArgumentException("推流密钥长度不能超过 50 个字符");
        }
        return normalized;
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

    private String generatePushKey() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ControllerException notFound(String message) {
        return new ControllerException(404, message);
    }
}
