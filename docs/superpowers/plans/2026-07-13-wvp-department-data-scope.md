# WVP Department Data Scope and System Administration UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **实施状态（2026-07-14）：已完成。** 后端全量测试 243 项（0 失败、1 项既有跳过），前端 Jest 17 个 suite/60 项全部通过，ESLint 0 error，生产构建成功，430 个 HTTP Handler 与机器权限矩阵双向一致。按用户要求未执行本计划中的任何 Git 提交步骤。

**Goal:** 增加若依式部门树和角色部门数据范围，并把它严格用于用户、部门、他人 API Key 与人员操作日志，不参与摄像头、通道或流资源过滤。

**Architecture:** `DepartmentScopeService` 先按 permissionCode 找到真正授予该权限的角色，再合并这些角色的部门范围；查询服务显式把 `DepartmentScope` 传给 Mapper，命令服务在修改前执行对象级检查。部门树使用 closure 表，不使用 ThreadLocal SQL 拼接或 MySQL 专有函数。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring Security 6、MyBatis 3、H2/MySQL/PostgreSQL/Kingbase、JUnit 5、Vue 2、Element UI、Jest。

## Global Constraints

- 必须先完成计划 1，并复用 `AuthPrincipal`、`CurrentPrincipalService`、`PermissionService` 和 `TokenVersionService`。
- `PermissionService` 必须提供 `findGrantingRoleIds(long userId, String permissionCode)`；范围只能合并这些角色，不能跨角色拼接。
- 用户只有一个主部门；部门不是租户，也不表示摄像头归属。
- 部门范围只作用于用户、部门、他人 API Key 和带操作者部门的管理日志。
- `CUSTOM` 只包含保存时明确提交到 `wvp_role_dept` 的部门 ID；前端“父子联动”会提交当时选中的后代，新建子部门不会自动扩大 CUSTOM 范围。
- `DEPT_AND_CHILD` 才动态包含当前用户主部门未来新增的后代。
- 空集合、停用部门、闭包异常和缓存异常全部 fail closed。
- 列表过滤在数据库完成；不得先查全量再由 Java 或前端删除。
- 本计划继续使用 `authorization.mode=audit`：decision scope 始终真实计算，effective scope 对旧接口兼容全量；新增授权配置接口标记为 always-enforce，避免无权限用户篡改权限数据。

---

## 1. 文件结构与责任

### 新增后端文件

| 路径 | 责任 |
|---|---|
| `src/main/java/com/genersoft/iot/vmp/auth/department/model/DepartmentScope.java` | ALL/CUSTOM/DEPT/DEPT_AND_CHILD/SELF/NONE 计算结果 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/model/DepartmentScopeType.java` | 范围枚举 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/model/Department.java` | 部门领域/持久化对象 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentScopeService.java` | 按权限解析部门范围 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentTreeService.java` | 新增、移动、停用、删除部门 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentCommandPolicy.java` | 用户/部门命令对象级检查 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentMapper.java` | 部门和闭包查询 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/RoleDepartmentMapper.java` | 角色自定义部门关系 |
| `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentController.java` | `/api/system/dept` |
| `src/main/java/com/genersoft/iot/vmp/auth/department/dto/*.java` | 部门命令、树和列表 DTO |
| `src/main/java/com/genersoft/iot/vmp/auth/user/SystemUserController.java` | `/api/system/user` 新接口 |
| `src/main/java/com/genersoft/iot/vmp/auth/user/SystemUserService.java` | 带范围的用户查询和命令 |

### 新增前端文件

| 路径 | 责任 |
|---|---|
| `web/src/api/system/dept.js` | 部门管理 API |
| `web/src/api/system/user.js` | 新用户管理 API |
| `web/src/api/system/role.js` | 角色、菜单、部门范围 API |
| `web/src/api/system/menu.js` | 菜单管理 API |
| `web/src/views/system/dept/index.vue` | 部门树维护 |
| `web/src/views/system/user/index.vue` | 左部门树、右用户列表 |
| `web/src/views/system/user/components/UserForm.vue` | 用户与多角色表单 |
| `web/src/views/system/role/index.vue` | 角色列表 |
| `web/src/views/system/role/components/RoleForm.vue` | 菜单树、部门范围表单 |
| `web/src/views/system/menu/index.vue` | 菜单/按钮维护 |

---

### Task 1: 创建部门表、闭包表和角色部门关系

**Files:**
- Modify: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0-h2/h2-schema.sql`
- Modify: `数据库/2.8.0-h2/h2-data.sql`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentMigrationTest.java`

**Interfaces:**
- Produces: `wvp_dept`、`wvp_dept_closure`、`wvp_role_dept`。
- Produces: `wvp_user.dept_id` 指向主部门。

- [ ] **Step 1: 写失败的结构契约测试**

```java
assertEquals(1, jdbc.queryForObject(
        "select count(*) from wvp_dept where dept_code='ROOT'", Integer.class));
assertEquals(1, jdbc.queryForObject(
        "select count(*) from wvp_dept_closure where ancestor_id=id and descendant_id=id and depth=0",
        Integer.class));
```

测试还应验证 `wvp_role_dept(role_id,dept_id)` 联合主键和 `wvp_user.dept_id` 索引存在。

- [ ] **Step 2: 写等价 DDL**

```sql
create table wvp_dept (
  id bigint primary key,
  parent_id bigint not null default 0,
  dept_name varchar(64) not null,
  dept_code varchar(64) not null,
  order_num integer not null default 0,
  leader_user_id bigint,
  status smallint not null default 0,
  deleted smallint not null default 0,
  create_by bigint,
  create_time timestamp not null,
  update_by bigint,
  update_time timestamp not null,
  constraint uk_wvp_dept_code unique (dept_code)
);

create table wvp_dept_closure (
  ancestor_id bigint not null,
  descendant_id bigint not null,
  depth integer not null,
  primary key (ancestor_id, descendant_id),
  foreign key (ancestor_id) references wvp_dept(id) on delete cascade,
  foreign key (descendant_id) references wvp_dept(id) on delete cascade
);

create index idx_wvp_dept_closure_desc
  on wvp_dept_closure(descendant_id, ancestor_id);

create table wvp_role_dept (
  role_id bigint not null,
  dept_id bigint not null,
  primary key (role_id, dept_id),
  foreign key (role_id) references wvp_role(id) on delete cascade,
  foreign key (dept_id) references wvp_dept(id) on delete cascade
);
```

- [ ] **Step 3: 回填根部门**

创建唯一 `ROOT` 部门，将所有旧用户 `dept_id` 设为根部门，并写 `(root,root,0)` 闭包关系。升级事务完成前执行：

```sql
select count(*) from wvp_user where dept_id is null;
```

Expected: 0。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=DepartmentMigrationTest test`

Expected: PASS，根部门、闭包和用户回填完整。

```bash
git add 数据库/2.8.0 数据库/2.8.0-h2 src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentMigrationTest.java
git commit -m "feat: add department hierarchy schema"
```

---

### Task 2: 实现跨数据库部门树事务

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/model/Department.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentTreeService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/impl/DepartmentTreeServiceImpl.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentTreeServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentMapperTest.java`

**Interfaces:**
- Produces: `long create(CreateDepartmentCommand command, AuthPrincipal operator)`。
- Produces: `void move(long deptId, long newParentId, AuthPrincipal operator)`。
- Produces: `void delete(long deptId, AuthPrincipal operator)`。
- Produces: `Set<Long> findEnabledDescendantIds(long deptId)`。

- [ ] **Step 1: 写闭包行为失败测试**

构造 `ROOT -> A -> A1`，断言 A 后代为 `{A,A1}`；把 A 移到 B 后，旧 ROOT/A 关系正确删除并建立 B/A/A1；禁止把 A 移到 A1；有用户或子部门时禁止删除。

- [ ] **Step 2: 实现新增节点**

同一事务中先插入部门，再执行：

```sql
insert into wvp_dept_closure(ancestor_id, descendant_id, depth)
select ancestor_id, :newDeptId, depth + 1
from wvp_dept_closure
where descendant_id = :parentId;

insert into wvp_dept_closure(ancestor_id, descendant_id, depth)
values (:newDeptId, :newDeptId, 0);
```

MyBatis 使用绑定参数，不拼 ID 字符串。

- [ ] **Step 3: 实现移动与删除**

移动前锁定目标子树和新父路径，拒绝环。删除旧外部祖先到子树的 closure 行，再把新父所有祖先与子树笛卡尔关系写入。ROOT、存在用户、存在子节点的部门不可删除。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=DepartmentTreeServiceTest,DepartmentMapperTest test`

Expected: PASS，移动事务失败时树和闭包均回滚。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth/department src/test/java/com/genersoft/iot/vmp/auth/department
git commit -m "feat: manage department closure tree"
```

---

### Task 3: 实现与 permissionCode 绑定的部门范围解析

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/model/DepartmentScope.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/model/DepartmentScopeType.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/RoleDepartmentMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentScopeService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/impl/DepartmentScopeServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/service/PermissionService.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentScopeServiceTest.java`

**Interfaces:**
- Consumes: `Set<Long> PermissionService.findGrantingRoleIds(long userId, String permissionCode)`。
- Produces: `DepartmentScope DepartmentScopeService.resolveDecisionScope(AuthPrincipal principal, String permissionCode)`。
- Produces: `DepartmentScope DepartmentScopeService.resolveEffectiveScope(AuthPrincipal principal, String permissionCode)`。

- [ ] **Step 1: 补齐计划 1 的关键接口**

```java
public interface PermissionService {
    Set<String> resolvePermissions(long userId, long tokenVersion);
    Set<Long> findGrantingRoleIds(long userId, String permissionCode);
    boolean hasPermission(AuthPrincipal principal, String permissionCode);
}
```

查询 `findGrantingRoleIds` 必须同时过滤用户、角色、菜单状态，并只返回通过 `wvp_role_menu` 授予该 permissionCode 的角色。

- [ ] **Step 2: 写防跨角色串用测试**

角色 A 授予 `system:user:list + DEPT`，角色 B 授予 `system:dept:list + ALL`。解析 `system:user:list` 只能得到 DEPT，不能因为角色 B 为 ALL 而得到全量。另测角色 C 的 SELF 与角色 D 的 CUSTOM 同时授予同一权限时，结果必须保留 `includeSelf=true` 和 CUSTOM deptIds。

- [ ] **Step 3: 实现不可变范围对象**

```java
public record DepartmentScope(
        boolean all,
        Set<Long> deptIds,
        boolean includeSelf,
        long userId) {
    public DepartmentScope {
        deptIds = Set.copyOf(deptIds);
    }
    public static DepartmentScope all(long userId) {
        return new DepartmentScope(true, Set.of(), false, userId);
    }
    public static DepartmentScope none(long userId) {
        return new DepartmentScope(false, Set.of(), false, userId);
    }
    public static DepartmentScope self(long userId) {
        return new DepartmentScope(false, Set.of(), true, userId);
    }
}
```

- [ ] **Step 4: 实现六种范围合并**

对授权角色逐个计算：ALL 立即返回 all；CUSTOM 取 `wvp_role_dept` 的启用部门；DEPT 取主体当前主部门；DEPT_AND_CHILD 从 closure 取启用后代；SELF 设置 includeSelf；NONE 不贡献范围。最后并集 deptIds，并保留 includeSelf。超级管理员直接 all。decisionScope 永远返回真实结果；effectiveScope 在 enforce 返回真实范围，在 audit 返回兼容全量并记录真实差异。

- [ ] **Step 5: 运行测试并提交**

Run: `mvn -Dtest=DepartmentScopeServiceTest test`

Expected: 六种范围、SELF+CUSTOM、多角色并集、停用部门、空 CUSTOM、audit/effective 和跨角色用例全部 PASS。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth src/test/java/com/genersoft/iot/vmp/auth/department
git commit -m "feat: resolve permission-coupled department scopes"
```

---

### Task 4: 在数据库层过滤用户和部门查询

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/user/SystemUserService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/user/impl/SystemUserServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/UserMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/user/dto/UserQuery.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/user/dto/UserSummary.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/user/SystemUserQueryTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/user/UserScopeSqlProviderTest.java`

**Interfaces:**
- Produces: `PageInfo<UserSummary> query(UserQuery query, DepartmentScope scope)`。
- Produces: `Optional<UserSummary> findVisible(long userId, DepartmentScope scope)`。

- [ ] **Step 1: 写分页范围失败测试**

数据包含 ROOT、A、A1、B 用户。分别验证 ALL、A、A+A1、SELF、NONE；分页 `total` 必须等于授权后的数量，搜索条件不能绕开范围。

- [ ] **Step 2: 使用显式参数构建范围 SQL**

Provider 必须生成等价逻辑：

```sql
and (
  :scopeAll = true
  or u.dept_id in (:deptIds)
  or (:includeSelf = true and u.id = :scopeUserId)
)
```

实现时对空 deptIds 不生成 `IN ()`，而是只保留 SELF 条件；NONE 生成恒假条件 `and 1 = 0`。所有值使用 `#{}` 绑定。

- [ ] **Step 3: 部门树也按有效范围裁剪**

部门列表不能先返回完整组织树。返回所授权部门及其为展示路径所必需的祖先；祖先节点标记 `readOnlyPath=true`，不能因此获得操作其用户的权限。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=SystemUserQueryTest,UserScopeSqlProviderTest test`

Expected: PASS，分页 total、搜索和树路径均不泄露范围外用户数据。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth/user src/main/java/com/genersoft/iot/vmp/storager/dao/UserMapper.java src/test/java/com/genersoft/iot/vmp/auth
git commit -m "feat: filter system users by department scope"
```

---

### Task 5: 为用户和部门命令增加对象级策略

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentCommandPolicy.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/impl/DepartmentCommandPolicyImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/user/impl/SystemUserServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/department/impl/DepartmentTreeServiceImpl.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentCommandPolicyTest.java`

**Interfaces:**
- Produces: `void requireUserInScope(long targetUserId, DepartmentScope scope)`。
- Produces: `void requireDepartmentInScope(long targetDeptId, DepartmentScope scope)`。
- Produces: `void requireRoleAssignmentAllowed(AuthPrincipal operator, Set<Long> roleIds)`。

- [ ] **Step 1: 写越权命令失败测试**

覆盖修改范围外用户、把用户移到范围外部门、给自己加角色、普通管理员操作超级管理员、删除自己的主部门、SELF 修改角色/部门/status 字段。

- [ ] **Step 2: 实现命令策略**

SELF 只允许当前用户修改 nickname 和密码；角色、主部门、状态、锁定和删除字段全部拒绝。用户变更角色/部门/状态/密码后，在同一业务事务内登记 tokenVersion 递增，并在事务提交后清缓存。

- [ ] **Step 3: 防止请求字段越权**

新增、编辑和自助修改使用不同 DTO；禁止把 User 实体直接作为 `@RequestBody`。例如：

```java
public record UpdateOwnProfileRequest(
        @NotBlank @Size(max = 64) String nickname) {}

public record AdminUpdateUserRequest(
        @NotBlank @Size(max = 64) String nickname,
        long deptId,
        Set<Long> roleIds,
        int status) {}
```

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=DepartmentCommandPolicyTest test`

Expected: 所有越权修改拒绝；合法管理员操作成功并递增授权版本。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth src/test/java/com/genersoft/iot/vmp/auth
git commit -m "feat: enforce department command policies"
```

---

### Task 6: 提供部门、用户、角色和菜单管理 API

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/auth/department/DepartmentController.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/user/SystemUserController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/RoleController.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/menu/SystemMenuController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/controller/SystemAdministrationControllerTest.java`

**Interfaces:**
- Produces: `/api/system/dept`、`/api/system/user`、`/api/system/role`、`/api/system/menu`。
- Preserves: `/api/user`、`/api/role` 兼容适配一个版本并返回弃用 Header。

- [ ] **Step 1: 写权限与范围组合测试**

有 `system:user:edit` 但目标不在部门范围时返回 404；有范围但无功能权限时返回 403；全部满足时返回 200/204。

- [ ] **Step 2: 固定方法权限**

```java
@PreAuthorize("@authz.require('system:dept:list')")
@GetMapping("/tree")
public List<DepartmentTreeNode> tree() {
    AuthPrincipal principal = currentPrincipalService.requireCurrent();
    DepartmentScope scope = departmentScopeService.resolveEffectiveScope(
            principal, "system:dept:list");
    return departmentTreeService.tree(scope);
}

@PreAuthorize("@authz.require('system:user:edit')")
@PutMapping("/{id}")
public void update(@PathVariable long id,
                   @Valid @RequestBody AdminUpdateUserRequest request) {
    systemUserService.update(id, request.toCommand());
}
```

角色保存接口在一个事务中更新角色、菜单和 CUSTOM 部门 ID；更改后找出角色全部用户并递增 tokenVersion。

- [ ] **Step 3: 统一兼容接口**

旧接口只能调用新应用服务，不能保留旧 `roleId==1` 分支和无范围 Mapper。响应添加：

```text
Deprecation: true
Sunset: Wed, 01 Jul 2027 00:00:00 GMT
Link: </api/system/user>; rel="successor-version"
```

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=SystemAdministrationControllerTest test`

Expected: 401/403/404/成功四种路径与弃用 Header 全部通过。

```bash
git add src/main/java/com/genersoft/iot/vmp/auth src/main/java/com/genersoft/iot/vmp/vmanager/user src/test/java/com/genersoft/iot/vmp/auth/controller
git commit -m "feat: add scoped system administration APIs"
```

---

### Task 7: 把部门范围用于他人 API Key 和授权操作审计

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/UserApiKeyController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/UserApiKeyServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/UserApiKeyMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/audit/AuthorizationAuditQueryService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/audit/impl/AuthorizationAuditQueryServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/AuthzAuditMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/provider/AuthzAuditProvider.java`
- Create: `src/main/java/com/genersoft/iot/vmp/vmanager/system/log/SystemOperationLogController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/log/LogController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentManagedResourceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/audit/AuthzAuditProviderTest.java`

**Interfaces:**
- Produces: 管理他人 Key 时同时要求 `system:apikey:manageOthers` 和目标用户落在部门范围。
- Produces: 授权操作审计按写入时的 `principal_dept_id` 快照过滤。

- [ ] **Step 1: 写任意 userId 越权失败测试**

普通用户把查询参数 userId 换成他人时返回 404；部门管理员只能访问范围内用户；没有 manageOthers 权限时即使部门范围匹配也返回 403。

- [ ] **Step 2: 显式传递 DepartmentScope**

API Key Mapper 不再提供无约束 `selectAll()` 给 HTTP 用例；系统定时任务如需全量查询，使用命名清晰的 `selectAllForInternalMaintenance()` 并只从 InternalPrincipal 服务调用。

- [ ] **Step 3: 过滤人员操作日志**

现有 `vmanager/log/LogController -> ILogService -> LogServiceImpl` 是服务器文件日志，不是人员操作审计：它只检查全局 `operations:log:view/download`，不套部门范围。新 SystemOperationLogController 查询 `wvp_authz_audit`，显式接收 DepartmentScope，并按写入时的 `principal_dept_id` 过滤；不能 JOIN 用户当前 dept。系统/媒体节点记录不伪造部门，只有权限对应范围为 ALL 的主体可查看无部门记录。

- [ ] **Step 4: 运行测试并提交**

Run: `mvn -Dtest=DepartmentManagedResourceTest,AuthzAuditProviderTest test`

Expected: userId 替换、日志越权和空范围均被拒绝。

```bash
git add src/main/java/com/genersoft/iot/vmp/vmanager src/main/java/com/genersoft/iot/vmp/service src/main/java/com/genersoft/iot/vmp/storager src/test/java/com/genersoft/iot/vmp/auth/department
git commit -m "feat: scope managed credentials and logs by department"
```

---

### Task 8: 实现若依式系统管理前端

**Files:**
- Create: `web/src/api/system/dept.js`
- Create: `web/src/api/system/user.js`
- Create: `web/src/api/system/role.js`
- Create: `web/src/api/system/menu.js`
- Create: `web/src/views/system/dept/index.vue`
- Create: `web/src/views/system/user/index.vue`
- Create: `web/src/views/system/user/components/UserForm.vue`
- Create: `web/src/views/system/role/index.vue`
- Create: `web/src/views/system/role/components/RoleForm.vue`
- Create: `web/src/views/system/menu/index.vue`
- Modify: `web/src/views/user/index.vue`
- Test: `web/tests/unit/views/system/UserManagement.spec.js`
- Test: `web/tests/unit/views/system/RoleForm.spec.js`
- Test: `web/tests/unit/views/system/DepartmentTree.spec.js`

**Interfaces:**
- Consumes: Task 6 的四组系统 API。
- Produces: 部门只出现在用户/角色数据范围页面，不出现在摄像头编辑页。

- [ ] **Step 1: 写交互失败测试**

用户页加载部门树后把选中 deptId 传给分页 API；角色表单切换 ALL/DEPT/DEPT_AND_CHILD/SELF/NONE 时隐藏 CUSTOM 树；CUSTOM 保存精确 checkedKeys；无权限按钮由 `v-hasPermi` 移除。

- [ ] **Step 2: 实现用户页**

左侧部门树用于查询过滤，右侧表格包含用户名、昵称、部门、角色、状态和操作。新增/编辑使用多选角色；重置密码、停用和删除各自使用独立权限编码。旧 `web/src/views/user/index.vue` 改为跳转兼容页或删除其菜单入口，不能同时维护两套业务实现。

- [ ] **Step 3: 实现角色和部门页**

角色表单分区为基本信息、菜单/按钮树、部门数据范围。CUSTOM 树的“父子联动”只影响保存时展开的 ID；表单下方明确提示“以后新增子部门不会自动获得权限”。部门页禁止删除 ROOT、有子部门或有用户的节点。

- [ ] **Step 4: 实现菜单页**

F 节点必须填写唯一 permissionCode；component 使用下拉白名单而不是自由路径。系统内置菜单禁止删除，只允许调整可见性和排序的字段由后端决定。

- [ ] **Step 5: 运行测试并提交**

Run: `cd web && npm run test:unit -- --runInBand tests/unit/views/system`

Expected: 3 个 suite PASS。

Run: `cd web && npm run lint`

Expected: 退出码 0。

```bash
git add web/src/api/system web/src/views/system web/src/views/user web/tests/unit/views/system
git commit -m "feat: add department-scoped system administration UI"
```

---

### Task 9: 部门范围集成验收

**Files:**
- Create: `src/test/java/com/genersoft/iot/vmp/auth/department/DepartmentScopeIntegrationTest.java`
- Create: `docs/security/department-scope-matrix.md`
- Modify: `docs/superpowers/specs/2026-07-13-wvp-rbac-dept-resource-design.md`

**Interfaces:**
- Produces: 计划 3 可消费的稳定 `DepartmentScopeService`，但视频服务禁止调用它过滤摄像头。

- [ ] **Step 1: 建立六种范围测试矩阵**

矩阵行固定为 ALL、CUSTOM、DEPT、DEPT_AND_CHILD、SELF、NONE；列为用户 list/detail/add/edit/remove/resetPwd/changeStatus、部门 list/add/edit/remove、他人 API Key、操作日志。每格记录期望 ALLOW/DENY/FILTER。

- [ ] **Step 2: 执行后端验收**

Run: `mvn -Dtest=DepartmentScopeIntegrationTest,DepartmentScopeServiceTest,DepartmentCommandPolicyTest,DepartmentManagedResourceTest test`

Expected: 测试数大于 0 且全部 PASS。

- [ ] **Step 3: 静态证明部门未进入视频模型**

```bash
rg "deptId|dept_id|DepartmentScope" \
  src/main/java/com/genersoft/iot/vmp/gb28181 \
  src/main/java/com/genersoft/iot/vmp/jt1078 \
  src/main/java/com/genersoft/iot/vmp/streamProxy \
  src/main/java/com/genersoft/iot/vmp/streamPush
```

Expected: 没有为了视频授权新增的命中；已有与人员无关的同名协议字段需在矩阵解释，但不能调用 DepartmentScopeService。

- [ ] **Step 4: 提交**

```bash
git add src/test docs/security docs/superpowers/specs/2026-07-13-wvp-rbac-dept-resource-design.md
git commit -m "test: verify department data boundaries"
```

## 完成定义

- 部门树和 closure 表在 H2 契约测试中正确新增、移动和删除。
- 六种部门范围按 permissionCode 选择授予角色，不发生跨角色拼接。
- 用户分页总数、详情和命令均在后端执行范围检查。
- SELF 不能修改自己的角色、部门或状态。
- 他人 API Key 和人员操作日志受部门范围控制。
- 前端具备用户、角色、部门、菜单管理页。
- 摄像头、通道、录像、告警、推拉流数据没有 dept_id 授权依赖。
- 配置仍保持 `authorization.mode=audit`，等待视频资源授权完成。
