package com.genersoft.iot.vmp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.config.AuthorizationProperties;
import com.genersoft.iot.vmp.auth.dto.LoginRequest;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;
import com.genersoft.iot.vmp.auth.dto.RouteDto;
import com.genersoft.iot.vmp.auth.menu.MenuComponentRegistry;
import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.auth.model.VerifiedAccessToken;
import com.genersoft.iot.vmp.auth.repository.AuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.auth.repository.PermissionRepository;
import com.genersoft.iot.vmp.auth.repository.impl.MyBatisAuthorizationAuditSink;
import com.genersoft.iot.vmp.auth.repository.impl.MyBatisPermissionRepository;
import com.genersoft.iot.vmp.auth.security.InvalidAccessTokenException;
import com.genersoft.iot.vmp.auth.security.JsonAccessDeniedHandler;
import com.genersoft.iot.vmp.auth.security.LegacyMd5PasswordEncoder;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.auth.service.LoginAttemptService;
import com.genersoft.iot.vmp.auth.service.PermissionEnforcer;
import com.genersoft.iot.vmp.auth.service.PermissionService;
import com.genersoft.iot.vmp.auth.service.TokenRevocationService;
import com.genersoft.iot.vmp.auth.service.TokenVersionService;
import com.genersoft.iot.vmp.auth.service.impl.MenuRouteServiceImpl;
import com.genersoft.iot.vmp.auth.service.impl.PermissionEnforcerImpl;
import com.genersoft.iot.vmp.auth.service.impl.PermissionServiceImpl;
import com.genersoft.iot.vmp.auth.service.impl.TokenVersionServiceImpl;
import com.genersoft.iot.vmp.auth.service.impl.UserAuthenticationServiceImpl;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.AuthzAuditMapper;
import com.genersoft.iot.vmp.storager.dao.MenuMapper;
import com.genersoft.iot.vmp.storager.dao.PermissionQueryMapper;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.utils.DateUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.DigestUtils;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@MybatisTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class FunctionalRbacIntegrationTest {

    private static final String PROBE_PERMISSION = "video:device:remove";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserMapper userMapper;
    @Autowired private PermissionQueryMapper permissionQueryMapper;
    @Autowired private MenuMapper menuMapper;
    @Autowired private AuthzAuditMapper authzAuditMapper;

    private PermissionCache permissionCache;
    private PermissionRepository permissionRepository;
    private PermissionService permissionService;
    private TokenVersionService tokenVersionService;
    private CurrentPrincipalService currentPrincipalService;
    private AuthorizationAuditSink auditSink;
    private IUserService userService;
    private JwtUtils jwtUtils;

    @BeforeEach
    void setUp() {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);
        seedIntegrationUsers();

        permissionCache = new NoOpPermissionCache();
        permissionRepository = new MyBatisPermissionRepository(permissionQueryMapper);
        permissionService = new PermissionServiceImpl(
                permissionRepository,
                permissionCache,
                properties(AuthorizationMode.AUDIT));
        tokenVersionService = new TokenVersionServiceImpl(userMapper, permissionCache);
        currentPrincipalService = new CurrentPrincipalService();
        auditSink = new MyBatisAuthorizationAuditSink(authzAuditMapper);
        userService = databaseBackedUserService();
        jwtUtils = tokenStub();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void anonymousDisabledAndOldVersionTokensAreRejectedWith401() throws Exception {
        assertEquals(401, perform(AuthorizationMode.ENFORCE, null).getStatus());
        assertEquals(401, perform(AuthorizationMode.ENFORCE, "disabled-token").getStatus());
        assertEquals(200, perform(AuthorizationMode.ENFORCE, "super-token").getStatus());

        tokenVersionService.bumpUser(3L, "INTEGRATION_TOKEN_INVALIDATION");
        assertEquals(401, perform(AuthorizationMode.ENFORCE, "operator-token").getStatus());
    }

    @Test
    void multiRoleUnionSystemRoleExpansionAndDynamicMenuPruningUseTheSameDatabaseRelations() {
        Set<String> operatorPermissions = permissionService.resolvePermissions(3L, 0L);
        assertEquals(Set.of("video:channel:list", "video:channel:play"), operatorPermissions);

        Set<String> superPermissions = permissionService.resolvePermissions(1L, 0L);
        Integer enabledPermissionCount = jdbcTemplate.queryForObject("""
                select count(*) from wvp_menu
                 where status = 0 and perms is not null and perms <> ''
                """, Integer.class);
        assertTrue(permissionRepository.isSystemUser(1L));
        assertEquals(enabledPermissionCount.intValue(), superPermissions.size());
        assertTrue(superPermissions.contains("system:user:remove"));
        assertTrue(superPermissions.contains("video:channel:play"));

        MenuRouteServiceImpl menuService = new MenuRouteServiceImpl(
                currentPrincipalService, menuMapper, new MenuComponentRegistry());
        setPrincipal(principal(3L));
        List<RouteDto> operatorRoutes = menuService.currentRoutes();
        assertEquals(List.of("Video"), operatorRoutes.stream().map(RouteDto::name).toList());
        assertEquals(List.of("Channel"),
                operatorRoutes.getFirst().children().stream().map(RouteDto::name).toList());

        setPrincipal(principal(2L));
        List<RouteDto> systemAdminRoutes = menuService.currentRoutes();
        assertEquals(List.of("System"), systemAdminRoutes.stream().map(RouteDto::name).toList());
        assertEquals(List.of("SystemUser"),
                systemAdminRoutes.getFirst().children().stream().map(RouteDto::name).toList());
    }

    @Test
    void auditRecordsRealDenyButAllowsCompatibilityWhileEnforceReturns403() throws Exception {
        assertEquals(200, perform(AuthorizationMode.AUDIT, "operator-token").getStatus());
        Integer auditDenies = jdbcTemplate.queryForObject("""
                select count(*) from wvp_authz_audit
                 where principal_id = 3
                   and permission_code = ?
                   and decision = 'DENY'
                """, Integer.class, PROBE_PERMISSION);
        assertEquals(1, auditDenies);

        assertEquals(403, perform(AuthorizationMode.ENFORCE, "operator-token").getStatus());
        Integer enforceDenies = jdbcTemplate.queryForObject("""
                select count(*) from wvp_authz_audit
                 where principal_id = 3
                   and permission_code = ?
                   and decision = 'DENY'
                """, Integer.class, PROBE_PERMISSION);
        assertEquals(2, enforceDenies);
    }

    @Test
    void rawPasswordLoginUpgradesLegacyMd5ToBcryptAndInvalidatesVersionZeroToken() {
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        when(authenticationManager.authenticate(any()))
                .thenReturn(UsernamePasswordAuthenticationToken.authenticated(
                        "operator", null, List.of()));
        LoginAttemptService loginAttemptService = mock(LoginAttemptService.class);
        UserSetting settings = userSetting();
        PasswordEncoder passwordEncoder = new DelegatingPasswordEncoder("bcrypt", Map.of(
                "bcrypt", new BCryptPasswordEncoder(),
                "legacy-md5", new LegacyMd5PasswordEncoder()));
        UserAuthenticationServiceImpl authenticationService = new UserAuthenticationServiceImpl(
                authenticationManager,
                userService,
                passwordEncoder,
                jwtUtils,
                settings,
                properties(AuthorizationMode.AUDIT),
                loginAttemptService,
                auditSink);

        LoginResponse response = authenticationService.login(
                new LoginRequest("operator", "plain-secret"), "127.0.0.1");
        User upgraded = userMapper.selectById(3L);

        assertEquals("upgraded-token", response.accessToken());
        assertEquals(1, upgraded.getPasswordVersion());
        assertEquals(1L, upgraded.getTokenVersion());
        assertTrue(upgraded.getPassword().startsWith("{bcrypt}"));
        assertTrue(passwordEncoder.matches("plain-secret", upgraded.getPassword()));
        assertFalse(tokenVersionService.matches(3L, 0L));
    }

    private MockHttpServletResponse perform(AuthorizationMode mode, String token) throws Exception {
        PermissionEnforcer enforcer = new PermissionEnforcerImpl(
                permissionService,
                currentPrincipalService,
                auditSink,
                properties(mode),
                new MockEnvironment().withProperty("spring.profiles.active", "test"));
        ObjectMapper objectMapper = new ObjectMapper();
        AnonymousAuthenticationEntryPoint authenticationEntryPoint =
                new AnonymousAuthenticationEntryPoint(objectMapper);
        JsonAccessDeniedHandler accessDeniedHandler = new JsonAccessDeniedHandler(objectMapper);
        JwtAuthenticationFilter authenticationFilter = new JwtAuthenticationFilter(
                jwtUtils,
                tokenVersionService,
                mock(TokenRevocationService.class),
                userService,
                permissionRepository,
                permissionService,
                userSetting(),
                authenticationEntryPoint);
        SecurityContextHolder.clearContext();
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/api/integration/permission-probe");
        if (token != null) {
            request.addHeader(JwtUtils.HEADER, token);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        authenticationFilter.doFilter(request, response, (servletRequest, servletResponse) -> {
            try {
                if (!enforcer.check(PROBE_PERMISSION)) {
                    throw new AccessDeniedException("permission denied");
                }
                ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_OK);
            } catch (AuthenticationException exception) {
                authenticationEntryPoint.commence(
                        (HttpServletRequest) servletRequest,
                        (HttpServletResponse) servletResponse,
                        exception);
            } catch (AccessDeniedException exception) {
                accessDeniedHandler.handle(
                        (HttpServletRequest) servletRequest,
                        (HttpServletResponse) servletResponse,
                        exception);
            }
        });
        return response;
    }

    private AuthPrincipal principal(long userId) {
        User user = userMapper.selectById(userId);
        long tokenVersion = user.getTokenVersion();
        return new AuthPrincipal(
                PrincipalType.USER,
                userId,
                userId,
                user.getUsername(),
                user.getDeptId(),
                Set.copyOf(permissionRepository.findEnabledRoleIds(userId)),
                tokenVersion,
                permissionRepository.isSystemUser(userId),
                permissionService.resolvePermissions(userId, tokenVersion));
    }

    private void setPrincipal(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, principal.authorities()));
    }

    private IUserService databaseBackedUserService() {
        IUserService service = mock(IUserService.class);
        when(service.getUserById(anyLong()))
                .thenAnswer(invocation -> userMapper.selectById(invocation.getArgument(0)));
        when(service.getUserByUsername(anyString()))
                .thenAnswer(invocation -> userMapper.getUserByUsername(invocation.getArgument(0)));
        when(service.upgradePassword(anyLong(), anyString(), anyString(), anyInt()))
                .thenAnswer(invocation -> userMapper.upgradePassword(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2),
                        invocation.getArgument(3),
                        DateUtil.getNow()) > 0);
        doAnswer(invocation -> {
            userMapper.updateLastLogin(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(service).updateLastLogin(anyLong(), any(LocalDateTime.class));
        return service;
    }

    private JwtUtils tokenStub() {
        JwtUtils stub = mock(JwtUtils.class);
        when(stub.verifyAccessToken(anyString())).thenAnswer(invocation -> switch ((String) invocation.getArgument(0)) {
            case "super-token" -> verified(1L, 0L, "super-jti");
            case "system-admin-token" -> verified(2L, 0L, "system-admin-jti");
            case "operator-token" -> verified(3L, 0L, "operator-jti");
            case "disabled-token" -> verified(4L, 0L, "disabled-jti");
            case "upgraded-token" -> verified(3L, 1L, "upgraded-jti");
            default -> throw new InvalidAccessTokenException("测试令牌无效");
        });
        when(stub.createAccessToken(anyLong(), anyLong(), any(Duration.class)))
                .thenReturn("upgraded-token");
        return stub;
    }

    private VerifiedAccessToken verified(long userId, long tokenVersion, String jti) {
        Instant issuedAt = Instant.parse("2026-07-13T00:00:00Z");
        return new VerifiedAccessToken(
                userId, jti, tokenVersion, issuedAt, issuedAt.plus(Duration.ofHours(1)));
    }

    private AuthorizationProperties properties(AuthorizationMode mode) {
        return new AuthorizationProperties(mode, false, Duration.ofMinutes(5));
    }

    private UserSetting userSetting() {
        UserSetting settings = new UserSetting();
        settings.setDocEnable(true);
        settings.setLoginTimeout(60);
        settings.setServerId("wvp-test");
        return settings;
    }

    private void seedIntegrationUsers() {
        String legacyPassword = DigestUtils.md5DigestAsHex(
                "plain-secret".getBytes(StandardCharsets.UTF_8));
        jdbcTemplate.update("""
                insert into wvp_user
                    (id, username, password, role_id, nickname, status, locked, deleted,
                     token_version, password_version, push_key)
                values
                    (2, 'system_admin', ?, 2, '系统管理员', 0, 0, 0, 0, 0, 'system-push'),
                    (3, 'operator', ?, 3, '视频操作员', 0, 0, 0, 0, 0, 'operator-push'),
                    (4, 'disabled_user', ?, 3, '停用用户', 1, 0, 0, 0, 0, 'disabled-push')
                """, legacyPassword, legacyPassword, legacyPassword);
        jdbcTemplate.update("""
                insert into wvp_role
                    (id, name, role_key, role_sort, dept_data_scope, video_scope_type, status, system_role)
                values
                    (2, '系统管理员', 'system_admin', 10, 'ALL', 'NONE', 0, 0),
                    (3, '通道查看', 'channel_viewer', 20, 'SELF', 'NONE', 0, 0),
                    (4, '通道播放', 'channel_player', 30, 'SELF', 'NONE', 0, 0)
                """);
        jdbcTemplate.update("""
                insert into wvp_user_role_rel (user_id, role_id) values
                    (2, 2), (3, 3), (3, 4), (4, 3)
                """);
        jdbcTemplate.update("""
                insert into wvp_role_menu (role_id, menu_id) values
                    (2, 100), (2, 110), (2, 11001), (2, 11004),
                    (3, 300), (3, 320), (3, 32001),
                    (4, 300), (4, 320), (4, 32003)
                """);
    }

    private static final class NoOpPermissionCache implements PermissionCache {

        @Override
        public Optional<Set<String>> get(long userId, long tokenVersion) {
            return Optional.empty();
        }

        @Override
        public void put(long userId, long tokenVersion, Set<String> permissions, Duration ttl) {
        }

        @Override
        public void evictUser(long userId) {
        }
    }
}
