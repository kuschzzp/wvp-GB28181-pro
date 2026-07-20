# WVP Authentication and Functional RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **实施状态（2026-07-14）：已完成。** 后端全量测试 164 项（0 失败、1 项既有跳过），前端 Jest 14 个 suite/47 项全部通过，ESLint 0 error，402 个 HTTP Handler 与机器权限矩阵双向一致。按用户要求未执行本计划中的任何 Git 提交步骤。

**Goal:** 在不改动 GB28181、JT1078 和媒体业务流程的前提下，完成认证硬化、多角色功能 RBAC、后端动态菜单和前端动态路由底座。

**Architecture:** 新增 `auth` 边界承载当前主体、权限解析、令牌版本和端点分类；现有控制器只通过 Spring Method Security 或授权门面使用权限，不再读取角色 ID。第一阶段使用 `authorization.mode=audit`，先计算并记录权限结果，再在后续计划完成对象级授权后切到 `enforce`。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring Security 6、MyBatis 3、Redis、JUnit 5、Spring Security Test、Vue 2.6、Vue Router 3、Vuex 3、Jest。

## Global Constraints

- 基线固定为 wvp-pro 2.7.4、Java 21、Spring Boot 3.4.4、Vue 2，不升级主要框架。
- 不启动项目完成计划编写；执行阶段必须逐任务运行明确列出的测试。
- Token 只从 `access-token` Header 读取；URL token 兼容默认关闭并记录告警。
- 登录只接受 `POST /api/auth/login` JSON，浏览器不再发送 MD5 值。
- 用户可以拥有多个角色；权限编码使用 `domain:resource:action`。
- 超级管理员通过 `system_role` 判断，不再使用 `userId == 1` 或 `roleId == 1`。
- 前端菜单和按钮只控制展示，后端授权结果始终是唯一可信来源。
- 本计划结束时保持 `authorization.mode=audit`，不得提前对视频业务全面阻断。
- 数据库变更同时维护 MySQL、PostgreSQL/Kingbase 和 H2；最终全量方言验证在计划 4 完成。

---

## 1. 文件结构与责任

### 新增后端文件

| 路径 | 责任 |
|---|---|
| `src/main/java/com/genersoft/iot/vmp/auth/model/AuthPrincipal.java` | 不含密码的当前登录主体 |
| `src/main/java/com/genersoft/iot/vmp/auth/model/PrincipalType.java` | USER、API_KEY、INTERNAL 主体类型 |
| `src/main/java/com/genersoft/iot/vmp/auth/model/AuthorizationMode.java` | disabled、audit、enforce 模式 |
| `src/main/java/com/genersoft/iot/vmp/auth/config/AuthorizationProperties.java` | `authorization.*` 配置映射 |
| `src/main/java/com/genersoft/iot/vmp/auth/service/CurrentPrincipalService.java` | 从 SecurityContext 获取强类型主体 |
| `src/main/java/com/genersoft/iot/vmp/auth/service/PermissionService.java` | 按用户和权限版本解析功能权限 |
| `src/main/java/com/genersoft/iot/vmp/auth/service/TokenVersionService.java` | 递增 tokenVersion 并清理主体缓存 |
| `src/main/java/com/genersoft/iot/vmp/auth/service/UserAuthenticationService.java` | 登录、旧密码升级、用户状态检查 |
| `src/main/java/com/genersoft/iot/vmp/auth/service/MenuRouteService.java` | 返回当前用户菜单树和路由 DTO |
| `src/main/java/com/genersoft/iot/vmp/auth/annotation/PublicEndpoint.java` | 显式公开端点标记 |
| `src/main/java/com/genersoft/iot/vmp/auth/annotation/InternalEndpoint.java` | 内部协议/RPC 端点标记 |
| `src/main/java/com/genersoft/iot/vmp/auth/annotation/MediaHookEndpoint.java` | 媒体 Hook 端点标记 |
| `src/main/java/com/genersoft/iot/vmp/auth/controller/AuthController.java` | 登录、登出、profile |
| `src/main/java/com/genersoft/iot/vmp/auth/controller/MenuController.java` | 当前用户动态路由 |
| `src/main/java/com/genersoft/iot/vmp/auth/dto/LoginRequest.java` | 登录请求 |
| `src/main/java/com/genersoft/iot/vmp/auth/dto/LoginResponse.java` | 登录响应 |
| `src/main/java/com/genersoft/iot/vmp/auth/dto/ProfileResponse.java` | 当前用户、角色、权限和版本 |
| `src/main/java/com/genersoft/iot/vmp/auth/dto/RouteMetaDto.java` | 路由 meta |
| `src/main/java/com/genersoft/iot/vmp/auth/dto/RouteDto.java` | 安全的动态路由 DTO |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/MenuMapper.java` | 菜单及角色菜单查询 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/UserRoleMapper.java` | 用户多角色关系 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/Menu.java` | 菜单持久化对象 |

### 主要修改文件

| 路径 | 变更 |
|---|---|
| `pom.xml` | 让测试默认真正执行，增加 `spring-security-test` |
| `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java` | STATELESS、明确公开端点、401/403、方法权限 |
| `src/main/java/com/genersoft/iot/vmp/conf/security/JwtAuthenticationFilter.java` | 只解析 Header，加载 AuthPrincipal 和真实权限 |
| `src/main/java/com/genersoft/iot/vmp/conf/security/JwtUtils.java` | JWT 仅包含 userId、jti、tokenVersion、aud、iat、exp |
| `src/main/java/com/genersoft/iot/vmp/conf/security/DefaultUserDetailsServiceImpl.java` | 返回用户状态和真实密码编码 |
| `src/main/java/com/genersoft/iot/vmp/conf/security/SecurityUtils.java` | 返回 AuthPrincipal，不重建 LoginUser |
| `src/main/java/com/genersoft/iot/vmp/conf/GlobalExceptionHandler.java` | 401、403、404 与业务异常状态码 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/UserMapper.java` | 用户状态、tokenVersion、多角色查询 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/RoleMapper.java` | 新角色表字段和角色菜单关系 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/User.java` | 敏感字段隔离和新增身份字段 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/Role.java` | roleKey、status、systemRole、范围字段 |
| `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserController.java` | 兼容 API 适配，不再承载登录 |
| `src/main/java/com/genersoft/iot/vmp/vmanager/user/RoleController.java` | 新角色命令和方法权限 |
| `src/main/resources/application.yml` | authorization 配置默认值 |
| `src/main/resources/配置详情.yml` | 配置说明和生产约束 |
| `web/src/router/index.js` | 只保留常量路由和动态路由重置 |
| `web/src/permission.js` | 登录后加载 profile/routers 并注册路由 |
| `web/src/store/modules/user.js` | 当前用户、角色、权限、authzVersion |
| `web/src/store/modules/permission.js` | 动态路由白名单映射 |
| `web/src/store/getters.js` | roles、permissions、permission_routes |
| `web/src/utils/request.js` | 区分 401/403，禁止错误被吞掉 |

---

### Task 1: 打通后端安全测试与端点分类门禁

**Files:**
- Modify: `pom.xml`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/annotation/PublicEndpoint.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/annotation/InternalEndpoint.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/annotation/MediaHookEndpoint.java`
- Create: `src/test/java/com/genersoft/iot/vmp/auth/EndpointClassificationTest.java`
- Create: `src/test/resources/application-test.yml`

**Interfaces:**
- Produces: 三个运行时方法/类型注解；所有后续控制器方法必须使用其一，或使用模式感知的 `@PreAuthorize("@authz.check('permission:code')")`。对象级资源检查由 Controller 在调用业务服务前通过 `AuthorizationFacade` 完成。
- Produces: Maven 测试默认执行；仅打包时允许显式传 `-DskipTests`。

- [ ] **Step 1: 写出会暴露当前测试跳过问题的构建变更**

在 `pom.xml` 中增加测试依赖，并删除 Surefire 中硬编码的 `<skipTests>true</skipTests>`：

```xml
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter-test</artifactId>
    <version>3.0.4</version>
    <scope>test</scope>
</dependency>
```

Surefire 保留版本，配置改为：

```xml
<configuration>
    <useModulePath>false</useModulePath>
</configuration>
```

- [ ] **Step 2: 创建端点分类注解**

三个注解使用同一结构，只改变类名：

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PublicEndpoint {
    String reason();
}
```

`InternalEndpoint` 和 `MediaHookEndpoint` 同样要求非空 `reason`，防止只贴标签不解释边界。

- [ ] **Step 3: 写端点分类失败测试**

测试使用 `RequestMappingHandlerMapping` 枚举 `com.genersoft.iot.vmp` 控制器方法。以下条件任一满足才算已分类：

```java
boolean classified = AnnotatedElementUtils.hasAnnotation(method, PreAuthorize.class)
        || AnnotatedElementUtils.hasAnnotation(method, PublicEndpoint.class)
        || AnnotatedElementUtils.hasAnnotation(method, InternalEndpoint.class)
        || AnnotatedElementUtils.hasAnnotation(method, MediaHookEndpoint.class);
assertTrue(classified, () -> handlerMethod + " 未声明授权分类");
```

本任务先把断言限制在 `com.genersoft.iot.vmp.auth.controller`，计划 3 再扩大到全仓，以免 audit 阶段一次阻断 439 个旧端点。

- [ ] **Step 4: 运行测试并确认门禁有效**

Run: `mvn -Dtest=EndpointClassificationTest test`

Expected: 新增 AuthController 之前测试通过；临时增加一个无注解测试端点时失败并包含“未声明授权分类”，删除临时端点后恢复通过。

- [ ] **Step 5: 提交**

```bash
git add pom.xml src/main/java/com/genersoft/iot/vmp/auth/annotation src/test/java/com/genersoft/iot/vmp/auth/EndpointClassificationTest.java src/test/resources/application-test.yml
git commit -m "test: enforce security endpoint classification"
```

---

### Task 2: 建立 2.8.0 身份、角色和菜单数据结构

**Files:**
- Create: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Create: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Create: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Create: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Create: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Create: `数据库/2.8.0/更新-kingbase-2.8.0.sql`
- Create: `数据库/2.8.0-h2/h2-schema.sql`
- Create: `数据库/2.8.0-h2/h2-data.sql`
- Create: `src/test/java/com/genersoft/iot/vmp/auth/MigrationContractTest.java`

**Interfaces:**
- Produces: `wvp_role`、expand 阶段关系表 `wvp_user_role_rel`、`wvp_menu`、`wvp_role_menu`、`wvp_authz_audit`。
- Produces: `wvp_user.dept_id/status/locked/deleted/token_version/password_version/nickname`。
- Preserves: 旧角色主表 `wvp_user_role` 和 `wvp_user.role_id`，新后端双写一个兼容周期；最终重命名只在计划 4 contract 执行。

- [ ] **Step 1: 写 H2 迁移契约失败测试**

使用 `@JdbcTest`，在 `@BeforeEach` 中通过 `ResourceDatabasePopulator` + `FileSystemResource` 加载仓库根目录的 `数据库/2.8.0-h2/h2-schema.sql`、`h2-data.sql`；这些文件不在 classpath，不能误用 `@Sql`：

```java
ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
        new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
        new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"));
populator.execute(dataSource);
```

随后验证：

```java
assertEquals(1, jdbcTemplate.queryForObject(
        "select count(*) from wvp_role where role_key='super_admin' and system_role=1",
        Integer.class));
assertEquals(1, jdbcTemplate.queryForObject(
        "select count(*) from wvp_user_role_rel where user_id=1",
        Integer.class));
assertEquals(0L, jdbcTemplate.queryForObject(
        "select token_version from wvp_user where id=1", Long.class));
```

- [ ] **Step 2: 写完整身份与菜单 DDL**

四类脚本必须使用同一逻辑约束：

```sql
create table wvp_role (
  id bigint primary key,
  name varchar(64) not null,
  role_key varchar(64) not null unique,
  role_sort integer not null default 0,
  dept_data_scope varchar(24) not null default 'NONE',
  video_scope_type varchar(16) not null default 'NONE',
  status smallint not null default 0,
  system_role smallint not null default 0,
  create_by bigint,
  create_time timestamp not null,
  update_by bigint,
  update_time timestamp not null
);

create table wvp_user_role_rel (
  user_id bigint not null,
  role_id bigint not null,
  primary key (user_id, role_id),
  foreign key (user_id) references wvp_user(id) on delete cascade,
  foreign key (role_id) references wvp_role(id) on delete cascade
);

create table wvp_menu (
  id bigint primary key,
  parent_id bigint not null default 0,
  menu_name varchar(64) not null,
  menu_type char(1) not null,
  route_name varchar(64),
  path varchar(255),
  component varchar(255),
  perms varchar(128),
  icon varchar(100),
  order_num integer not null default 0,
  visible smallint not null default 0,
  status smallint not null default 0,
  cacheable smallint not null default 0,
  external smallint not null default 0,
  system_menu smallint not null default 0,
  create_by bigint,
  create_time timestamp not null,
  update_by bigint,
  update_time timestamp not null,
  constraint uk_wvp_menu_perms unique (perms)
);

create table wvp_role_menu (
  role_id bigint not null,
  menu_id bigint not null,
  primary key (role_id, menu_id),
  foreign key (role_id) references wvp_role(id) on delete cascade,
  foreign key (menu_id) references wvp_menu(id) on delete cascade
);
```

expand 前先把现有 `wvp_user.id` 及 `wvp_user_api_key.user_id` 按各方言安全扩为 BIGINT，随后再创建引用它们的 BIGINT 关系表；H2 契约测试同时验证 Java long/Long 与数据库 BIGINT 映射。旧 `wvp_user.role_id` 兼容列保持原值，`wvp_role` 使用 BIGINT 接收复制后的角色 ID。

升级脚本采用 expand-contract：新建 `wvp_role` 并从旧 `wvp_user_role` 复制，新建 `wvp_user_role_rel` 并从旧 `wvp_user.role_id` 回填；不在本计划重命名或删除旧表。如果发现用户或角色关系不一致，事务必须失败回滚。contract 前可回滚旧应用；contract 后只能恢复数据库快照。

同时创建 `wvp_authz_audit`，字段至少包含 `principal_type/principal_id/principal_dept_id/permission_code/resource_type/resource_id/decision/reason_code/request_id/client_ip/create_time`。`principal_dept_id` 固化操作时部门，禁止历史查询关联用户当前部门。

- [ ] **Step 3: 写权限种子数据**

种子至少完整覆盖设计文档第 8 节的系统权限和视频业务权限。F 类型节点保存完整 `perms`，例如 `video:channel:play`；M/C 节点只保存路由信息。超级管理员不依赖 `wvp_role_menu` 全量插入，运行时通过 `system_role=1` 展开所有启用菜单。

升级得到的普通 `legacy_role_<id>` 必须写入显式 `LEGACY_COMPAT` 关系：保留 2.7.4 已有的视频、运维、用户查询和本人 API Key 能力，但不得包含角色/部门/菜单管理、他人 API Key 或系统角色能力。兼容授权只对 `update_by is null` 的迁移角色播种；管理员第一次保存角色时，服务在同一事务中整体替换 `wvp_role_menu` 并写入 `update_by`，此后重复执行升级脚本不得重新放大权限。

- [ ] **Step 4: 运行迁移契约测试**

Run: `mvn -Dtest=MigrationContractTest test`

Expected: PASS；重复执行更新脚本时要么由版本表阻止，要么安全退出，不产生重复用户角色关系。

- [ ] **Step 5: 提交**

```bash
git add 数据库/2.8.0 数据库/2.8.0-h2 src/test/java/com/genersoft/iot/vmp/auth/MigrationContractTest.java
git commit -m "feat: add identity and menu schema"
```

---

### Task 3: 定义无密码主体与权限解析接口

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/model/AuthPrincipal.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/model/PrincipalType.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/model/AuthorizationMode.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/config/AuthorizationProperties.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/CurrentPrincipalService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/PermissionService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/impl/PermissionServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/PermissionEnforcer.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/impl/PermissionEnforcerImpl.java`
- Create: `src/test/java/com/genersoft/iot/vmp/auth/service/PermissionServiceTest.java`
- Create: `src/test/java/com/genersoft/iot/vmp/auth/service/PermissionEnforcerTest.java`

**Interfaces:**
- Produces: `Set<String> PermissionService.resolvePermissions(long userId, long tokenVersion)`。
- Produces: `Set<Long> PermissionService.findGrantingRoleIds(long userId, String permissionCode)`。
- Produces: `boolean PermissionService.hasPermission(AuthPrincipal principal, String permissionCode)`。
- Produces: Spring Bean `authz` 的 `boolean check(String permissionCode)`，供受灰度模式控制的业务接口使用；`boolean require(String permissionCode)` 供授权配置接口始终强制鉴权。
- Produces: `AuthPrincipal CurrentPrincipalService.requireCurrent()`。

- [ ] **Step 1: 写多角色权限合并失败测试**

```java
@Test
void resolvesEnabledPermissionsFromAllEnabledRoles() {
    when(repository.findPermissionCodes(12L)).thenReturn(List.of(
            "video:channel:list", "video:channel:play", "video:channel:play"));
    assertEquals(Set.of("video:channel:list", "video:channel:play"),
            service.resolvePermissions(12L, 7L));
}

@Test
void systemRoleGetsEveryEnabledPermission() {
    when(repository.isSystemUser(1L)).thenReturn(true);
    when(repository.findAllEnabledPermissionCodes()).thenReturn(List.of("system:user:list"));
    assertEquals(Set.of("system:user:list"), service.resolvePermissions(1L, 3L));
}
```

- [ ] **Step 2: 实现主体类型**

```java
public record AuthPrincipal(
        PrincipalType type,
        long id,
        Long userId,
        String username,
        Long deptId,
        Set<Long> roleIds,
        long tokenVersion,
        boolean systemUser,
        Set<String> permissions) implements Principal {

    public AuthPrincipal {
        roleIds = Set.copyOf(roleIds);
        permissions = Set.copyOf(permissions);
    }

    @Override
    public String getName() {
        return username;
    }

    public Collection<GrantedAuthority> authorities() {
        return permissions.stream().map(SimpleGrantedAuthority::new).toList();
    }
}
```

不得在 record 中增加 password、API Key 明文或媒体票据。

- [ ] **Step 3: 实现授权模式配置**

```java
@ConfigurationProperties(prefix = "authorization")
public record AuthorizationProperties(
        AuthorizationMode mode,
        boolean allowUrlTokenCompatibility,
        Duration principalCacheTtl) {
    public AuthorizationProperties {
        mode = mode == null ? AuthorizationMode.AUDIT : mode;
        principalCacheTtl = principalCacheTtl == null ? Duration.ofMinutes(5) : principalCacheTtl;
    }
}
```

`application.yml` 使用：

```yaml
authorization:
  mode: audit
  allow-url-token-compatibility: false
  principal-cache-ttl: 5m
```

- [ ] **Step 4: 实现权限解析并运行测试**

Permission 查询必须过滤用户、角色和菜单状态；Redis key 使用 `auth:principal:{userId}:{tokenVersion}`。缓存异常时抛出认证/授权异常，不能返回空集合后继续放行。`findGrantingRoleIds` 只返回实际通过启用 `wvp_role_menu` 授予当前 permissionCode 的角色，供计划 2/3 计算范围，禁止跨角色拼接。第一版不增加未落库的 roleVersion、deptVersion 或 groupVersion 缓存。

`PermissionEnforcer` 永远计算真实 decision：disabled 只允许 dev/test 并兼容放行；audit 记录真实 DENY 后返回 true；enforce 返回真实判定。生产配置为 disabled 时启动失败。受灰度模式控制的业务端点禁止直接使用 `hasAuthority`。

Run: `mvn -Dtest=PermissionServiceTest,PermissionEnforcerTest test`

Expected: PASS，重复权限去重，停用角色和停用菜单不出现在结果中。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/genersoft/iot/vmp/auth src/test/java/com/genersoft/iot/vmp/auth src/main/resources/application.yml src/main/resources/配置详情.yml
git commit -m "feat: add authorization principal and permissions"
```

---

### Task 4: 重写登录与旧密码平滑升级

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/security/LegacyMd5PasswordEncoder.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/UserAuthenticationService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/impl/UserAuthenticationServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/dto/LoginRequest.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/dto/LoginResponse.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/controller/AuthController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/DefaultUserDetailsServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/controller/AuthControllerTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/security/PasswordMigrationTest.java`

**Interfaces:**
- Produces: `POST /api/auth/login`，请求 `LoginRequest(username,password)`。
- Produces: `LoginResponse(accessToken, expiresAt, serverId)`；不返回 User/LoginUser/password。
- Preserves: `/api/user/login` 只保留 POST 兼容适配一个版本，并返回 `Deprecation` 与 `Sunset` Header。

- [ ] **Step 1: 写登录协议失败测试**

使用 `@WebMvcTest(AuthController.class)` 与 `spring-security-test`：

```java
mockMvc.perform(get("/api/auth/login"))
        .andExpect(status().isMethodNotAllowed());

mockMvc.perform(post("/api/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"username\":\"operator\",\"password\":\"plain-secret\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.accessToken").isString())
        .andExpect(jsonPath("$.data.password").doesNotExist());
```

- [ ] **Step 2: 实现旧 MD5 编码器与 DelegatingPasswordEncoder**

```java
public final class LegacyMd5PasswordEncoder implements PasswordEncoder {
    @Override
    public String encode(CharSequence rawPassword) {
        return DigestUtils.md5DigestAsHex(rawPassword.toString().getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return MessageDigest.isEqual(
                encode(rawPassword).getBytes(StandardCharsets.US_ASCII),
                encodedPassword.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }
}
```

密码 Bean：

```java
Map<String, PasswordEncoder> encoders = Map.of(
        "bcrypt", new BCryptPasswordEncoder(),
        "legacy-md5", new LegacyMd5PasswordEncoder());
return new DelegatingPasswordEncoder("bcrypt", encoders);
```

读取旧 32 位无前缀密码时在 UserDetails 适配器中加 `{legacy-md5}`；验证成功后立即写回 `{bcrypt}` 编码。

- [ ] **Step 3: 实现账号状态和失败锁定**

认证前依次检查 `deleted=0`、`status=0`、`locked=0`。失败计数 Redis key 为 `auth:login-failure:{username}:{clientIp}`，10 分钟内连续 5 次失败锁定 15 分钟；成功后删除失败计数。锁定状态和失败原因写授权审计，不回显“用户不存在”还是“密码错误”。

- [ ] **Step 4: 实现新登录 DTO 和兼容入口**

`LoginRequest` 使用 Bean Validation：用户名 `@NotBlank @Size(max=64)`，密码 `@NotBlank @Size(max=128)`。新控制器只接收原始密码 JSON。旧 GET 立即 405；旧 POST 兼容由 `authorization.legacy-md5-login-enabled=false` 控制，默认关闭。短期开启时只允许 passwordVersion=LEGACY 且“客户端 MD5 与数据库旧 MD5”恒定时间比较，返回 Deprecation/Sunset Header；由于服务端不知道原始密码，不得把该登录伪装成 BCrypt 升级。

- [ ] **Step 5: 运行认证测试**

Run: `mvn -Dtest=AuthControllerTest,PasswordMigrationTest test`

Expected: GET 登录为 405；停用/锁定/删除用户为 401；旧 MD5 首次成功后数据库变为 `{bcrypt}`；响应 JSON 不含 password。

- [ ] **Step 6: 提交**

```bash
git add src/main/java/com/genersoft/iot/vmp/auth src/main/java/com/genersoft/iot/vmp/conf/security src/main/java/com/genersoft/iot/vmp/vmanager/user/UserController.java src/test/java/com/genersoft/iot/vmp/auth
git commit -m "feat: harden login and migrate passwords"
```

---

### Task 5: 精简 JWT 并实现权限版本失效

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/JwtUtils.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/JwtAuthenticationFilter.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/SecurityUtils.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/TokenVersionService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/impl/TokenVersionServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/TokenRevocationService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/LogoutHandler.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/security/JwtAuthenticationFilterTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/service/TokenVersionServiceTest.java`

**Interfaces:**
- Produces: `String JwtUtils.createAccessToken(long userId, long tokenVersion, Duration ttl)`。
- Produces: `VerifiedAccessToken JwtUtils.verifyAccessToken(String token)`。
- Produces: `void TokenVersionService.bumpUser(long userId, String reason)`。
- Produces: `void TokenVersionService.bumpUsers(Collection<Long> userIds, String reason)`。
- Produces: `void TokenVersionService.bumpUsersByRoleId(long roleId, String reason)`。
- Produces: `boolean TokenVersionService.matches(long userId, long tokenVersion)`。
- Produces: `void TokenRevocationService.revoke(VerifiedAccessToken token)`。

- [ ] **Step 1: 写 token 来源和版本失败测试**

覆盖六个断言：Header token 成功；query token 默认忽略；API Key 不进入 JWT filter；tokenVersion 不一致返回 401；已撤销 jti 返回 401；Authentication credentials 为 null。

```java
assertNull(SecurityContextHolder.getContext().getAuthentication().getCredentials());
assertEquals(Set.of("video:channel:play"),
        AuthorityUtils.authorityListToSet(authentication.getAuthorities()));
```

- [ ] **Step 2: 定义最小 JWT Claims**

```java
public record VerifiedAccessToken(
        long userId,
        String jti,
        long tokenVersion,
        Instant issuedAt,
        Instant expiresAt) {}
```

生成 token 只写 `sub=userId`、`jti`、`tokenVersion`、`iat`、`exp`、`aud=wvp-web`。删除 userName、password、roleId 和 API Key 兼容解析。

- [ ] **Step 3: 重写过滤器**

过滤器算法固定为：读取 Header → 验签 → 查询用户状态/tokenVersion → 解析权限 → 构造 AuthPrincipal → 用真实 authorities 创建 `UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)`。`interface-authentication=false` 不能再构造空主体；生产配置出现该值时启动失败。

- [ ] **Step 4: 实现版本递增**

Mapper 使用原子 SQL：

```sql
update wvp_user
set token_version = token_version + 1, update_time = current_timestamp
where id in
<foreach collection="userIds" item="userId" open="(" separator="," close=")">
  #{userId}
</foreach>
and deleted = 0
```

提交事务后删除旧 `auth:principal:{userId}:*` 索引集合；不得在数据库事务提交前先删缓存。

退出把 jti 写入 `auth:revoked:{jti}`，TTL 等于 token 剩余时间。Redis 写失败时递增当前 user.token_version，使该用户全部会话失效；数据库更新也失败则返回 503，不能记录“退出成功”后继续接受旧 token。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -Dtest=JwtAuthenticationFilterTest,TokenVersionServiceTest test`

Expected: PASS；tokenVersion 变化后旧 token 为 401，角色变化后权限缓存不再命中。

```bash
git add src/main/java/com/genersoft/iot/vmp/conf/security src/main/java/com/genersoft/iot/vmp/auth src/test/java/com/genersoft/iot/vmp/auth
git commit -m "feat: enforce versioned access tokens"
```

---

### Task 6: 统一 401、403、404 与 STATELESS 安全配置

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/security/JsonAccessDeniedHandler.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/AnonymousAuthenticationEntryPoint.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/GlobalExceptionHandler.java`
- Modify: `src/main/resources/application-docker.yml`
- Modify: `docker/wvp/wvp/application-base.yml`
- Modify: `docker/wvp/wvp/application-docker.yml`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/security/SecurityStatusCodeTest.java`

**Interfaces:**
- Produces: 未认证 401、功能权限不足 403、被隐藏的无权对象 404。

- [ ] **Step 1: 写 HTTP 语义失败测试**

```java
mockMvc.perform(get("/api/auth/profile"))
        .andExpect(status().isUnauthorized());
mockMvc.perform(get("/api/auth/admin-probe").with(user("viewer")))
        .andExpect(status().isForbidden());
```

- [ ] **Step 2: 收紧安全链**

使用 `@EnableMethodSecurity` 替换已弃用的 `@EnableGlobalMethodSecurity`；Session 改为 `STATELESS`；普通用户安全链的公开路径只保留静态资源、`POST /api/auth/login`、分享交换和 liveness。快照、录像、JT 下载、日志 WebSocket 与 `record_proxy` 不再公开。为保证本计划不改变既有收拉流行为，`/index/hook/**` 先进入最高优先级、无 JWT/API Key 过滤器的独立 `DEFERRED_MEDIA_AUTH` 兼容链；它不能重新混入普通 `permitAll` 清单。计划 4 用 `MediaHookAuthenticationFilter` 原位替换该兼容链的临时放行规则。

- [ ] **Step 3: 实现统一 JSON 错误**

```java
public record SecurityError(int code, String msg, String requestId) {}
```

认证入口写 HTTP 401，拒绝处理器写 HTTP 403。`GlobalExceptionHandler` 显式处理 `AccessDeniedException`，不得被通用 Exception 转为 500，也不得用 HTTP 200 包装 ERROR403。

- [ ] **Step 4: 收紧 CORS**

当 `allowed-origins` 为空时不注册跨域来源；只有显式来源列表才能 `allowCredentials=true`。测试断言恶意 Origin 不出现 `Access-Control-Allow-Origin`。

同时修正 Docker 配置中当前 `interface-authentication:false` 和 `/api/**` 排除。生产 profile 强制认证开启；`interface-authentication` 只保留一个版本的弃用映射，不能与 `authorization.mode` 形成第二条放行路径。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -Dtest=SecurityStatusCodeTest test`

Expected: 401、403、CORS 与 STATELESS 断言全部通过。

```bash
git add src/main/java/com/genersoft/iot/vmp/conf src/main/java/com/genersoft/iot/vmp/auth/security src/test/java/com/genersoft/iot/vmp/auth/security
git commit -m "feat: normalize security responses"
```

---

### Task 7: 实现用户多角色、角色菜单与方法权限

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/User.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/Role.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/Menu.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/UserRoleMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/MenuMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/UserMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/RoleMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/UserServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/RoleServerImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/RoleController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/service/RoleAssignmentServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/vmanager/user/UserAuthorizationTest.java`

**Interfaces:**
- Produces: `void assignRoles(long operatorId, long userId, Set<Long> roleIds)`。
- Produces: `void saveRole(RoleCommand command, Set<Long> menuIds)`，单事务替换角色菜单。

- [ ] **Step 1: 写多角色事务测试**

测试必须验证：重复角色去重；停用角色拒绝分配；非超级管理员不能分配 `system_role=1`；更新后受影响用户 tokenVersion 增加一次。

- [ ] **Step 2: 实现关系替换而非逐条残留更新**

```java
@Transactional
public void assignRoles(long operatorId, long userId, Set<Long> roleIds) {
    roleAssignmentPolicy.check(operatorId, userId, roleIds);
    userRoleMapper.deleteByUserId(userId);
    if (!roleIds.isEmpty()) {
        userRoleMapper.insertBatch(userId, new TreeSet<>(roleIds));
    }
    tokenVersionService.bumpUsers(List.of(userId), "USER_ROLES_CHANGED");
}
```

兼容字段 `wvp_user.role_id` 双写排序最小的角色 ID；它不参与权限计算。

- [ ] **Step 3: 替换硬编码管理员判断**

用户和角色控制器方法使用稳定权限：

```java
@PreAuthorize("@authz.require('system:user:add')")
@PostMapping
public void createUser(@Valid @RequestBody CreateUserRequest request) {
    systemUserService.create(request.toCommand());
}

@PreAuthorize("@authz.require('system:role:assign')")
@PutMapping("/{userId}/roles")
public void assignRoles(@PathVariable long userId,
                        @RequestBody @Valid AssignRolesRequest request) {
    systemUserService.assignRoles(userId, request.roleIds());
}
```

删除所有 `getRole().getId() != 1` 分支。超级管理员保护在 policy/service 层重复执行，不能只依赖注解。

- [ ] **Step 4: 防止敏感字段序列化**

控制器返回专用 DTO；同时给持久化对象的 password、pushKey、API Key 明文加 `@JsonIgnore` 作为第二道防线。新增测试把所有用户与登录响应序列化，断言不包含 `password`、`apiKey`、`credentials`。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -Dtest=RoleAssignmentServiceTest,UserAuthorizationTest test`

Expected: PASS；普通角色不能创建用户、不能分配超级管理员角色，超级管理员保护不依赖固定 ID。

```bash
git add src/main/java/com/genersoft/iot/vmp/storager src/main/java/com/genersoft/iot/vmp/service src/main/java/com/genersoft/iot/vmp/vmanager/user src/test/java/com/genersoft/iot/vmp
git commit -m "feat: add multi-role functional rbac"
```

---

### Task 8: 提供 profile 和安全动态路由 API

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/dto/ProfileResponse.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/dto/RouteDto.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/dto/RouteMetaDto.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/MenuRouteService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/service/impl/MenuRouteServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/controller/MenuController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/controller/AuthController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/service/MenuRouteServiceTest.java`

**Interfaces:**
- Produces: `GET /api/auth/profile`。
- Produces: `GET /api/menu/routers`。
- Produces: 组件值只允许 `Layout` 或后端定义的逻辑组件键。

- [ ] **Step 1: 写菜单剪枝和组件白名单失败测试**

断言停用/隐藏节点不返回、无可见子节点目录被剪掉、按钮节点不进入路由、未知 component 抛出配置异常、超级管理员返回全部启用菜单。

- [ ] **Step 2: 实现 profile**

```java
public record ProfileResponse(
        UserSummary user,
        Set<String> roles,
        Set<String> permissions,
        long authzVersion) {}
```

响应不返回资源 ID 列表、密码、JWT claims 或 API Key。

- [ ] **Step 3: 实现路由树**

按 `parent_id` 构树并稳定排序 `order_num,id`。F 节点只贡献 permissions；M/C 节点转换 RouteDto。`component` 使用逻辑键，例如 `device/index`，前端再映射到固定 import。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=MenuRouteServiceTest test`

Expected: PASS；任何非法 component 都不会进入响应。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth src/test/java/com/genersoft/iot/vmp/auth
git commit -m "feat: expose profile and authorized routes"
```

---

### Task 9: 修复 API Key 明文、所有权和过期时间 P0

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/dto/UserApiKey.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/UserApiKeyMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/IUserApiKeyService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/UserApiKeyServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserApiKeyController.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeySecretHasher.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationFilter.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/apikey/dto/ApiKeyCreatedResponse.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/apikey/dto/ApiKeySummary.java`
- Modify: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0-h2/h2-schema.sql`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/apikey/ApiKeySecretHasherTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/apikey/UserApiKeyServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/apikey/UserApiKeyControllerSecurityTest.java`

**Interfaces:**
- Produces: `ApiKeyCreatedResponse create(long ownerId, CreateApiKeyCommand command)`，secret 只出现一次。
- Produces: `PageInfo<ApiKeySummary> pageOwnKeys(long ownerId, ApiKeyQuery query)`。
- Produces: `AuthPrincipal ApiKeyAuthenticationService.authenticate(String rawKey, InetAddress clientAddress)`；阶段 0 暂取所属用户当前权限，不得扩大。
- Defers: permission/resource scope、IP allowlist 与 LEGACY 清理在计划 4 完成。

- [ ] **Step 1: 写当前漏洞的失败测试**

验证数据库从不出现 raw secret；列表/详情不返回 secret/hash；创建/轮换响应只显示一次；任意 userId 不能列出他人 Key；expiredAt 使用绝对 UTC 时间；当前 Mapper 的 remark 更新不能再误写 username。

- [ ] **Step 2: 使用高熵 secret 和服务端 HMAC**

```java
public record GeneratedApiKey(String raw, String prefix, String last4, String hash) {}

public GeneratedApiKey generate() {
    byte[] bytes = new byte[32];
    secureRandom.nextBytes(bytes);
    String raw = "wvp_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    return new GeneratedApiKey(
            raw,
            raw.substring(0, 12),
            raw.substring(raw.length() - 4),
            hmacSha256(raw, serverPepper));
}
```

数据库字段使用 `key_hash/key_prefix/key_last4/expired_at/status/authz_version/last_used_at`；创建成功 DTO 携带 raw，持久化对象和列表 DTO 永远不携带 raw。

`authorization.api-key-pepper` 只从环境变量或 Secret 注入，生产缺失时启动失败；配置示例只写变量名，不把真实 pepper 提交到仓库。

- [ ] **Step 3: 固定本人/他人边界**

默认只能管理 CurrentPrincipal.userId 的 Key。管理他人必须拥有 `system:apikey:manageOthers`；部门范围在计划 2 叠加。旧控制器传入 userId 时必须忽略为本人或按 manageOthers 校验，不能直接传给 Mapper。

- [ ] **Step 4: 纠正时间单位和 LEGACY 状态**

API 接收 ISO-8601 或 UTC epoch millis，服务统一转为 Instant/绝对毫秒；不再把绝对时间传给当前把第二个参数解释为未来分钟数的 `JwtUtils.createToken(String username, Long expirationTime, Map<String, Object> extra)`。旧明文 Key 标记 LEGACY 并只保留受配置限制的兼容窗口；列表只显示掩码。

JWT Filter 不再读取 `api-key`。独立 ApiKeyAuthenticationFilter 验证 hash/LEGACY 兼容、owner 状态、expiredAt 和 authzVersion，构造 `PrincipalType.API_KEY` 且 credentials=null；计划 4 在同一服务上增加 permission/resource scope 与 IP allowlist。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -Dtest=ApiKeySecretHasherTest,ApiKeyAuthenticationServiceTest,UserApiKeyServiceTest,UserApiKeyControllerSecurityTest test`

Expected: PASS；P0 漏洞关闭，细粒度 scope 明确留给计划 4。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth/apikey src/main/java/com/genersoft/iot/vmp/storager src/main/java/com/genersoft/iot/vmp/service src/main/java/com/genersoft/iot/vmp/vmanager/user/UserApiKeyController.java 数据库/2.8.0 数据库/2.8.0-h2 src/test/java/com/genersoft/iot/vmp/auth/apikey
git commit -m "fix(security): harden API key storage and ownership"
```

---

### Task 10: 给全仓 HTTP 端点落功能权限和分类

**Files:**
- Modify: `src/test/java/com/genersoft/iot/vmp/auth/EndpointClassificationTest.java`
- Create: `src/test/resources/security/controller-permission-matrix.csv`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/ChannelController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/ChannelFrontEndController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceConfig.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceControl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceQuery.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/GBRecordController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/GroupController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/MediaController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/MobilePositionController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlatformController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlayController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlaybackController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PtzController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/RegionController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078Controller.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078TerminalController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/controller/StreamProxyController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/controller/StreamPushController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/TestController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/alarm/AlarmController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/cloudRecord/CloudRecordController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/log/LogController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/ps/PsController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/recordPlan/RecordPlanController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/rtp/RtpController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/server/ServerController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/RoleController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserApiKeyController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/gb28181/ApiControlController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/gb28181/ApiController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/gb28181/ApiDeviceController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/gb28181/ApiStreamController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/gb28181/AuthController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/custom/CameraChannelController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/zlm/ZLMHttpHookListener.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/abl/ABLHttpHookListener.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/service/PermissionEnforcerTest.java`

**Interfaces:**
- Produces: 每个实际 RequestMappingHandlerMapping 恰有一个 Public、Internal、MediaHook、RequirePermission 或 RequireResource 分类。
- Produces: RequireResource 端点先落功能权限；资源对象检查在计划 3 完成。

- [ ] **Step 1: 让矩阵和反射扫描双向校验**

CSV 列固定为 `handlerClass,handlerMethod,httpMethod,path,classification,permissionCode,resourceResolver,compatibilityStatus`。测试从 Spring MVC HandlerMapping 反射发现实际端点，不硬编码“37 个文件”或“439 个映射”；新增漏标、CSV 失效行、重复分类、非法 permissionCode 都失败。

- [ ] **Step 2: 分类所有映射源文件**

逐一覆盖 GB 控制器（包括不以 Controller 命名的 DeviceConfig/DeviceControl/DeviceQuery）、JT 控制器、ZLM/ABL Listener、推拉流、vmanager、web/custom 和 web/gb28181。Redis RPC Controller 如果不进入 Spring HTTP HandlerMapping，不写伪 HTTP 行；内部调用另用 InternalEndpoint/内部身份保护。

- [ ] **Step 3: 落模式感知功能权限**

受保护方法统一：

```java
@PreAuthorize("@authz.check('video:channel:play')")
```

需要资源的端点在 CSV 标记 RequireResource 和 resolver；计划 3 前只执行功能 decision，不能写假对象授权。Public/Internal/MediaHook 使用显式 marker 和非空 reason，不能只依赖 WebSecurityConfig 路径白名单。

- [ ] **Step 4: 校验权限编码和 audit 行为**

`PermissionEnforcerTest` 验证 disabled 仅 dev/test、audit 记录 DENY 但兼容放行、enforce 返回真实拒绝。矩阵权限编码必须匹配 `^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9-]*$`，以兼容 `share-channel`、`revoke-share` 等已批准 action。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -Dtest=PermissionEnforcerTest,EndpointClassificationTest test`

Expected: 反射发现的全部 HTTP 映射都有唯一分类，CSV 无遗漏或失效行。

```bash
git add src/main/java/com/genersoft/iot/vmp src/test/java/com/genersoft/iot/vmp/auth src/test/resources/security/controller-permission-matrix.csv
git commit -m "feat(rbac): classify and authorize HTTP endpoints"
```

---

### Task 11: 前端接入动态路由和按钮权限底座

**Files:**
- Modify: `web/src/router/index.js`
- Modify: `web/src/permission.js`
- Modify: `web/src/store/modules/user.js`
- Create: `web/src/store/modules/permission.js`
- Modify: `web/src/store/index.js`
- Modify: `web/src/store/getters.js`
- Create: `web/src/utils/permission.js`
- Create: `web/src/directive/permission/hasPermi.js`
- Create: `web/src/directive/permission/index.js`
- Modify: `web/src/main.js`
- Modify: `web/src/api/user.js`
- Create: `web/src/api/menu.js`
- Test: `web/tests/unit/store/permission.spec.js`
- Test: `web/tests/unit/directive/hasPermi.spec.js`
- Test: `web/tests/unit/permission-guard.spec.js`

**Interfaces:**
- Consumes: `GET /api/auth/profile`、`GET /api/menu/routers`。
- Produces: `store.getters.permissions`、`store.getters.permission_routes`、`v-hasPermi`、`this.$auth.hasPermi`。

- [ ] **Step 1: 写动态路由失败测试**

```javascript
expect(filterAsyncRoutes([{ component: 'device/index' }])[0].component)
  .toBe(componentMap['device/index'])
expect(() => filterAsyncRoutes([{ component: '../../evil' }]))
  .toThrow('未知路由组件')
```

守卫测试覆盖首次加载只请求一次、刷新恢复、登出清理、403 不退出登录。

- [ ] **Step 2: 缩减 constantRoutes**

只保留 `/login`、`/403`、`/404`、`/play/share/:shareId` 和必要静态页。将当前 dashboard、live、device、channel、operations 等全部业务路由移入后端菜单种子。通配 404 必须在动态路由最后加入。

- [ ] **Step 3: 创建显式组件白名单**

```javascript
export const componentMap = Object.freeze({
  Layout: () => import('@/layout'),
  'dashboard/index': () => import('@/views/dashboard/index'),
  'live/index': () => import('@/views/live/index'),
  'device/index': () => import('@/views/device/index'),
  'channel/index': () => import('@/views/channel/index'),
  'user/index': () => import('@/views/user/index')
})
```

把当前 `router/index.js` 中每个业务 component 逐一加入映射；禁止使用服务端字符串拼接 `import()`。

- [ ] **Step 4: 实现权限状态和指令**

```javascript
export function hasPermi(required, granted) {
  if (!Array.isArray(required) || required.length === 0) return false
  return required.some(code => granted.includes(code))
}
```

`v-hasPermi` 在 inserted 钩子中无权限则移除节点；代码路径使用同一个纯函数。按钮隐藏不替代后端拒绝。

- [ ] **Step 5: 重写登录调用和错误处理**

前端删除 `crypto` MD5，改为 POST JSON。响应拦截器 401 执行重新登录流程，403 展示“无权限”并 `Promise.reject(error)`；其他错误同样 reject，不能吞掉 Promise。

- [ ] **Step 6: 运行前端测试并提交**

Run: `cd web && npm run test:unit -- --runInBand tests/unit/store/permission.spec.js tests/unit/directive/hasPermi.spec.js tests/unit/permission-guard.spec.js`

Expected: 3 个 suite 全部 PASS。

```bash
git add web/src web/tests/unit
git commit -m "feat: add dynamic routes and permission directives"
```

---

### Task 12: 阶段 0～2 集成验收与 audit 模式交付

**Files:**
- Create: `src/test/java/com/genersoft/iot/vmp/auth/FunctionalRbacIntegrationTest.java`
- Create: `docs/security/endpoint-permission-matrix.md`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-07-13-wvp-rbac-dept-resource-design.md`

**Interfaces:**
- Produces: 后续计划可依赖的 `AuthPrincipal`、`PermissionService`、`TokenVersionService` 和动态路由协议。
- Produces: `authorization.mode=audit` 的可部署版本。

- [ ] **Step 1: 写集成测试**

固定四个用户：super_admin、system_admin、operator、disabled_user。验证匿名 401、多角色权限并集、system_role 展开、禁用用户旧 token 失效、密码升级和动态菜单剪枝。权限不足分别在 audit 断言“记录 DENY 但兼容放行”，在 enforce 断言 HTTP 403。

- [ ] **Step 2: 生成端点权限矩阵**

矩阵列固定为：HTTP Method、Path、Controller Method、Classification、Permission Code、Resource Type、Compatibility Status。此阶段列出全部端点；计划 3 完成前允许视频端点标记 `LEGACY_AUDIT`，但不允许空分类。

- [ ] **Step 3: 执行阶段验收命令**

Run: `mvn -Dtest=FunctionalRbacIntegrationTest,EndpointClassificationTest test`

Expected: 所有 auth 测试执行且 PASS，Surefire 汇总 `Tests run` 大于 0。

Run: `cd web && npm run test:unit -- --runInBand`

Expected: 全部 Jest suite PASS。

Run: `cd web && npm run lint`

Expected: 退出码 0。

- [ ] **Step 4: 人工静态检查**

```bash
rg "roleId\s*==\s*1|roleId\s*!=\s*1|getRole\(\)\.getId\(\)" src/main/java
rg "getParameter\(JwtUtils.getHeader\(\)\)|@GetMapping\(\"/login\"\)" src/main/java
rg "createHash\('md5'\)" web/src
```

Expected: 三条命令均无命中；确需兼容的路径必须在矩阵标为 `DEPRECATED` 且不保留不安全行为。

- [ ] **Step 5: 提交**

```bash
git add src/test docs README.md
git commit -m "docs: complete functional rbac audit rollout"
```

## 完成定义

- `mvn test` 不再静默跳过测试。
- 浏览器登录只走 POST JSON，旧 MD5 用户可平滑升级为 BCrypt。
- JWT 不含密码、角色 ID 和权限全集，tokenVersion 变化后失效。
- Authentication 中包含真实 GrantedAuthority，credentials 为 null。
- 用户支持多角色，角色支持菜单和按钮权限。
- API Key 不再明文存储或列表展示，过期时间和本人/他人边界已修复。
- `profile` 与 `routers` 可驱动前端动态路由。
- 401 与 403 行为不同，前端 403 不退出登录。
- 全部端点进入机器可检查矩阵，视频端点保持 audit 等待计划 3。
- 普通旧角色具有显式、可一次性退出且重复迁移不回授的 `LEGACY_COMPAT` 授权。
- ZLM/ABL Hook 与用户认证链隔离，现有回调不中断，节点身份认证仍明确留在计划 4。
- 工作区配置仍为 `authorization.mode=audit`。
