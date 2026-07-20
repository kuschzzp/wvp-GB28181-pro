# WVP Video Resource Groups and Object Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **实施状态（2026-07-15）：Task 1～12 已完成自动化阶段验收，用户侧业务验证进行中。** 438 个 HTTP Handler 已全部分类，286 个资源端点只使用 4 类真实解析器，7 个定向测试类共 94 项通过。旧 `ChannelProviderTest` 已补充强制 `VideoScopeQuery` 夹具，其自身 17 项通过；后端全量覆盖 91 个测试套件、497 项测试，0 失败、0 错误、1 项既有跳过。前端 20 个测试套件、96 项测试全部通过，ESLint 为 0 error（保留 680 条 warning）。按用户要求未执行 `git add`、`git commit` 或 `git push`；计划中的提交命令仅保留为原始变更边界参考。

**Goal:** 建立独立视频资源组、按权限耦合的角色资源范围、数据库列表过滤和对象级授权，覆盖 GB28181、JT1078、推流、拉流、录像、告警、位置和控制操作。

**Architecture:** 视频资源授权不使用部门树。`VideoResourceScopeService` 先取得授予当前 permissionCode 的角色，再计算这些角色关联的资源组；`ResourceResolver` 把外部设备编号、通道、recordId、alarmId、app/stream 等统一解析为内部资源主键；业务控制器只调用 `AuthorizationFacade`，列表 Mapper 显式消费 `VideoScopeQuery`。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring Security 6、MyBatis SQL Provider、Redis、JUnit 5、H2/MySQL/PostgreSQL/Kingbase、Vue 2、Element UI、Jest。

## Global Constraints

- 必须先完成计划 1 和计划 2。
- 部门和视频资源组是两套独立树；禁止给摄像头、通道、流、录像或告警增加 dept_id 授权关系。
- 视频资源组只授权给角色，不直接授权给用户；特殊用户通过专用角色实现。
- 计算权限 P 的资源范围时，只使用 `PermissionService.findGrantingRoleIds(userId, P)` 返回的角色。
- 多角色同一权限下取资源并集；任一授予角色为 ALL 则该权限为全部资源；NONE 不贡献范围。
- 设备/终端级授权动态包含当前和以后产生的通道；显式通道授权不放大成整台设备。
- 列表、分页总数、统计、导出在数据库过滤；按 ID 命令在业务动作前授权。
- 批量操作中任一对象无权，整个请求失败，不允许部分成功。
- 对象不存在和无权查看详情统一返回 404；明确控制命令可返回 403。
- `authorization.mode=audit` 下计算真实决策并记录，但旧端点保持兼容；测试使用 enforce 配置验证阻断。
- 新增 Java ID 使用 long/Long；外部 GB/JT 字符串编号先解析为内部主键。

---

## 1. 文件结构与责任

### 授权核心

| 路径 | 责任 |
|---|---|
| `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoResourceType.java` | 六类可绑定资源和派生资源枚举 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoScopeType.java` | ALL、CUSTOM、NONE 角色范围枚举 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoResourceRef.java` | 类型 + 内部主键 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/model/ResourceDenialMode.java` | FORBID 或 HIDE_AS_NOT_FOUND |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoScope.java` | 真实授权范围 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoScopeQuery.java` | Mapper 可消费的范围参数 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoResourceScopeService.java` | 按权限解析资源范围 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/ResourceAuthorizationService.java` | 单对象与批量要求 |
| `src/main/java/com/genersoft/iot/vmp/authorization/AuthorizationFacade.java` | 业务层唯一授权入口 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/ResourceResolver.java` | 资源解析 SPI |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/ChannelResourceResolver.java` | 统一通道、GB/JT 通道解析 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/RecordResourceResolver.java` | 录像回溯通道 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/AlarmResourceResolver.java` | 告警回溯通道 |
| `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/StreamResourceResolver.java` | proxy/push/app/stream 回溯资源 |

### 资源组管理

| 路径 | 责任 |
|---|---|
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/model/ResourceGroup.java` | 授权资源组 |
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/model/ResourceGroupItem.java` | 组内资源项 |
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupMapper.java` | 资源组和 closure |
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupItemMapper.java` | 资源项 |
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/RoleResourceGroupMapper.java` | 角色资源组 |
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupService.java` | 资源组树事务 |
| `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupController.java` | `/api/system/resource-group` |

### 前端

| 路径 | 责任 |
|---|---|
| `web/src/api/system/resourceGroup.js` | 资源组 API |
| `web/src/views/system/resourceGroup/index.vue` | 左树右资源列表 |
| `web/src/views/system/resourceGroup/components/ResourceGroupTree.vue` | 授权树，不复用业务分组数据 |
| `web/src/views/system/resourceGroup/components/ResourceItemDialog.vue` | 来源资源/通道选择 |
| `web/tests/unit/permissions/mediaActions.spec.js` | 业务按钮权限覆盖 |

---

### Task 1: 创建视频资源组、角色关系和派生资源字段

**Files:**
- Modify: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0-h2/h2-schema.sql`
- Modify: `数据库/2.8.0-h2/h2-data.sql`
- Test: `src/test/java/com/genersoft/iot/vmp/migration/VideoResourceSchemaH2Test.java`

**Interfaces:**
- Produces: `wvp_resource_group`、`wvp_resource_group_closure`、`wvp_resource_group_item`、`wvp_role_resource_group`。
- Produces: `wvp_cloud_record.source_type/source_id/channel_id/source_reason`。

- [x] **Step 1: 写结构契约失败测试**

断言 root 资源组存在自身 closure；item 唯一键拒绝重复；同一资源可以进入不同组；角色关系只指向启用组；云录像能保存来源通道。

- [x] **Step 2: 写等价 DDL**

```sql
create table wvp_resource_group (
  id bigint primary key,
  parent_id bigint not null default 0,
  group_code varchar(64) not null unique,
  group_name varchar(64) not null,
  order_num integer not null default 0,
  status smallint not null default 0,
  description varchar(500),
  create_by bigint,
  create_time timestamp not null,
  update_by bigint,
  update_time timestamp not null
);

create table wvp_resource_group_closure (
  ancestor_id bigint not null,
  descendant_id bigint not null,
  depth integer not null,
  primary key (ancestor_id, descendant_id),
  foreign key (ancestor_id) references wvp_resource_group(id) on delete cascade,
  foreign key (descendant_id) references wvp_resource_group(id) on delete cascade
);

create table wvp_resource_group_item (
  id bigint primary key,
  group_id bigint not null,
  resource_type varchar(32) not null,
  resource_id bigint not null,
  create_by bigint,
  create_time timestamp not null,
  constraint uk_wvp_resource_group_item
    unique (group_id, resource_type, resource_id),
  foreign key (group_id) references wvp_resource_group(id) on delete cascade
);

create table wvp_role_resource_group (
  role_id bigint not null,
  resource_group_id bigint not null,
  primary key (role_id, resource_group_id),
  foreign key (role_id) references wvp_role(id) on delete cascade,
  foreign key (resource_group_id) references wvp_resource_group(id) on delete cascade
);
```

增加 `idx_wvp_resource_group_closure_desc(descendant_id,ancestor_id)`、`idx_wvp_resource_item_ref(resource_type,resource_id)` 和 `idx_wvp_role_resource_group_group(resource_group_id,role_id)`。

- [x] **Step 3: 初始化安全默认值**

创建 `ROOT_VIDEO` 根组。super_admin 设置 `video_scope_type=ALL`；其他角色默认 NONE，不能因为升级而默认全资源。旧云录像的 `channel_id` 保持 NULL 并标记 `source_type='LEGACY_UNBOUND'`，等待 Task 7 回填；Task 7 已按唯一候选规则完成回填服务与原因持久化。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=VideoResourceSchemaH2Test test`

Expected: PASS；关系、索引和安全默认值全部满足。

```bash
git add 数据库/2.8.0 数据库/2.8.0-h2 src/test/java/com/genersoft/iot/vmp/migration/VideoResourceSchemaH2Test.java
git commit -m "feat(db): add video resource authorization schema"
```

---

### Task 2: 实现资源组 closure 树和资源项事务

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/model/ResourceGroup.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/model/ResourceGroupItem.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupItemMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/RoleResourceGroupMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/impl/ResourceGroupServiceImpl.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupServiceTest.java`

**Interfaces:**
- Produces: `long create(CreateResourceGroupCommand command)`。
- Produces: `void move(long groupId, long parentId)`。
- Produces: `void replaceItems(long groupId, Set<VideoResourceRef> items)`。
- Produces: `void replaceRoleGroups(long roleId, VideoScopeType type, Set<Long> groupIds)`。

- [x] **Step 1: 写树和关系失败测试**

覆盖新增、移动、环检测、停用、删除前迁移、一个资源多组、组内重复去重、非 CUSTOM 清空角色关系、system_role 的 ALL 不可降级。

- [x] **Step 2: 复用 closure 算法但不复用部门数据**

可以抽取纯算法/SQL helper，但 ResourceGroup Mapper、表和服务必须独立。移动事务锁定子树；ROOT_VIDEO 不可移动或删除。

- [x] **Step 3: 写资源项时校验真实存在**

```java
for (VideoResourceRef item : items) {
    resolverRegistry.requireExisting(item);
}
resourceGroupItemMapper.deleteByGroupId(groupId);
resourceGroupItemMapper.insertBatch(groupId, items);
```

泛型关系没有物理业务 FK；业务资源删除服务必须调用 `deleteByResource(type,id)`，另有每日孤儿扫描只删除确定不存在的项并写审计。

- [x] **Step 4: 角色范围变更使用户版本失效**

保存 role.video_scope_type 和组关系后，调用 `TokenVersionService.bumpUsersByRoleId(roleId, "ROLE_VIDEO_SCOPE_CHANGED")`；事务失败时关系和版本都回滚。

- [x] **Step 5: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=ResourceGroupServiceTest test`

Expected: PASS，树、资源项、角色关系和 tokenVersion 均符合预期。

```bash
git add src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup src/test/java/com/genersoft/iot/vmp/authorization/resourcegroup
git commit -m "feat(authz): manage independent video resource groups"
```

---

### Task 3: 建立统一资源类型与解析器注册表

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoResourceType.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoScopeType.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoResourceRef.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/ResourceResolver.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/ResourceResolverRegistry.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/ChannelResourceResolver.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/RecordResourceResolver.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/AlarmResourceResolver.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/resolver/StreamResourceResolver.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/ResourceResolverRegistryTest.java`

**Interfaces:**
- Produces: `VideoResourceRef requireExisting(VideoResourceType type, long id)`。
- Produces: `VideoResourceRef resolveGbChannel(String deviceId, String channelId)`。
- Produces: `VideoResourceRef resolveAppStream(String app, String stream)`。
- Produces: `VideoResourceRef resolveRecord(long recordId)`、`resolveAlarm(long alarmId)`。

- [x] **Step 1: 定义稳定枚举**

```java
public enum VideoResourceType {
    GB_DEVICE,
    CHANNEL,
    JT_TERMINAL,
    JT_CHANNEL,
    STREAM_PROXY,
    STREAM_PUSH,
    CLOUD_RECORD,
    DEVICE_RECORD,
    ALARM,
    MEDIA_STREAM,
    LEGACY_UNBOUND
}
```

角色范围枚举固定为：

```java
public enum VideoScopeType {
    ALL,
    CUSTOM,
    NONE
}
```

只有前六类可以写入资源组；后五类必须解析到来源通道/来源资源后授权。

- [x] **Step 2: 写歧义和缺失失败测试**

同一 channelId 出现在不同设备时必须结合 deviceId；同一 app/stream 只能解析到一个当前来源；资源不存在返回 NotFound；LEGACY_UNBOUND 只允许 systemUser。

- [x] **Step 3: 实现解析器 SPI**

```java
public interface ResourceResolver {
    Set<VideoResourceType> supportedTypes();
    VideoResourceRef requireExisting(VideoResourceRef resource);
    VideoResourceRef resolveToAuthorizable(VideoResourceRef resource);
}
```

Registry 启动时拒绝同一类型多个解析器或可绑定类型缺失解析器。解析结果只使用内部数据库 ID，不把 GB/JT 外部编号存入资源组 item.resource_id。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=ResourceResolverRegistryTest test`

Expected: 六类可绑定资源和五类派生资源解析测试全部 PASS。

```bash
git add src/main/java/com/genersoft/iot/vmp/authorization/video src/test/java/com/genersoft/iot/vmp/authorization/video
git commit -m "feat(authz): resolve video resources to stable IDs"
```

---

### Task 4: 实现按权限耦合的视频范围服务

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoScope.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/model/VideoScopeQuery.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoResourceScopeService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/impl/VideoResourceScopeServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoScopeMapper.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoResourceScopeServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoScopeProviderTest.java`

**Interfaces:**
- Consumes: `PermissionService.findGrantingRoleIds(userId, permissionCode)`。
- Produces: `VideoScope resolveDecisionScope(AuthPrincipal principal, String permissionCode)`。
- Produces: `VideoScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode)`。

- [x] **Step 1: 写跨角色防串用测试**

角色 A：`video:channel:play + 一号楼`；角色 B：`video:record:list + 二号楼`。play 范围只能含一号楼，record:list 只能含二号楼。再覆盖 ALL 短路、NONE、停用角色、停用组、父组包含子组和多组去重。

- [x] **Step 2: 定义可用于 SQL 的范围**

```java
public record VideoScope(
        boolean all,
        Set<Long> grantingRoleIds,
        Set<VideoResourceRef> sourceResources,
        Set<Long> explicitChannelIds) {
    public VideoScope {
        grantingRoleIds = Set.copyOf(grantingRoleIds);
        sourceResources = Set.copyOf(sourceResources);
        explicitChannelIds = Set.copyOf(explicitChannelIds);
    }
}
```

`VideoScopeQuery` 额外携带 permissionCode 和主体 ID，但不进入 JWT。大范围列表优先使用 grantingRoleIds 通过关系表 JOIN；小范围对象检查可用缓存后的集合。

- [x] **Step 3: 实现模式语义**

decisionScope 永远计算真实结果。effectiveScope 在 enforce 返回 decisionScope；audit 返回 all 并把真实差异写 `wvp_authz_audit`；disabled 只允许 dev/test。缓存故障时 decision 为 DENY，enforce 不得放行。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=VideoResourceScopeServiceTest,VideoScopeProviderTest test`

Expected: 多角色、继承、模式和 fail-closed 全部 PASS。

```bash
git add src/main/java/com/genersoft/iot/vmp/authorization/video src/test/java/com/genersoft/iot/vmp/authorization/video
git commit -m "feat(authz): resolve permission-coupled video scopes"
```

---

### Task 5: 提供统一对象授权门面和资源组管理 API

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/ResourceAuthorizationService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/model/ResourceDenialMode.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/impl/ResourceAuthorizationServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/AuthorizationFacade.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/impl/AuthorizationFacadeImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/user/RoleController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/ResourceAuthorizationServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupControllerTest.java`

**Interfaces:**
- Produces: `void requireVideoResource(String permissionCode, VideoResourceType type, long resourceId)`。
- Produces: `void requireVideoResource(String permissionCode, VideoResourceType type, long resourceId, ResourceDenialMode denialMode)`。
- Produces: `void requireAllVideoResources(String permissionCode, Collection<VideoResourceRef> resources)`。
- Produces: `VideoScope resolveVideoScope(String permissionCode)`。
- Produces: `VideoScopeQuery resolveVideoScopeQuery(String permissionCode)`。

- [x] **Step 1: 写单对象与批量失败测试**

覆盖允许、无功能权限、无资源、对象不存在、批量混入一个无权 ID、systemUser。无权详情映射 404，控制命令映射 403 由调用方声明 `ResourceDenialMode`。

- [x] **Step 2: 实现门面**

```java
public interface AuthorizationFacade {
    void requirePermission(String permissionCode);
    void requireVideoResource(String permissionCode,
                              VideoResourceType resourceType,
                              long resourceId);
    void requireVideoResource(String permissionCode,
                              VideoResourceType resourceType,
                              long resourceId,
                              ResourceDenialMode denialMode);
    void requireAllVideoResources(String permissionCode,
                                  Collection<VideoResourceRef> resources);
    VideoScope resolveVideoScope(String permissionCode);
    VideoScopeQuery resolveVideoScopeQuery(String permissionCode);
    DepartmentScope resolveDepartmentScope(String permissionCode);
}
```

门面依赖 CurrentPrincipalService 和专用 scope service；不能依赖业务 Controller。业务代码禁止直接查 role/resource_group 表。

- [x] **Step 3: 实现资源组 API**

`/api/system/resource-group` 使用 `system:resource-group:list/add/edit/remove/assign`。角色保存时菜单、部门范围和视频组关系在同一应用事务提交；新资源组 API 自身始终 enforce，避免无权限用户在 audit 期篡改授权配置。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=ResourceAuthorizationServiceTest,ResourceGroupControllerTest test`

Expected: PASS，批量原子拒绝和配置接口强制权限均正确。

```bash
git add src/main/java/com/genersoft/iot/vmp/authorization src/main/java/com/genersoft/iot/vmp/vmanager/user/RoleController.java src/test/java/com/genersoft/iot/vmp/authorization
git commit -m "feat(authz): expose resource authorization facade"
```

---

### Task 6: 过滤设备、统一通道、推流和拉流列表

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoListScopeSql.java`
- Create: `src/main/java/com/genersoft/iot/vmp/gb28181/dao/provider/DeviceProvider.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/dao/provider/ChannelProvider.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/dao/DeviceChannelMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/dao/provider/DeviceChannelProvider.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceQuery.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/ChannelController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/service/IDeviceService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/service/impl/DeviceServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/service/impl/GbChannelServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/dao/StreamProxyMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/dao/provider/StreamProxyProvider.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/controller/StreamProxyController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/service/IStreamProxyService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/service/impl/StreamProxyServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/dao/StreamPushMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/streamPush/dao/provider/StreamPushProvider.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/controller/StreamPushController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/service/IStreamPushService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/service/impl/StreamPushServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/gb28181/ApiDeviceController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoListScopeProviderTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoListScopeMapperTest.java`

**Interfaces:**
- Changes: 所有 HTTP 列表 service 方法增加 `VideoScopeQuery scope` 参数。
- Preserves: 内部维护任务使用显式 `selectAllForInternalMaintenance()` 方法，不传伪造 all scope。

- [x] **Step 1: 写列表和分页失败测试**

分别以 GB_DEVICE、CHANNEL、STREAM_PROXY、STREAM_PUSH 授权，验证来源级覆盖通道、通道级不放大、搜索/在线状态/分页 total 仍受范围限制。

- [x] **Step 2: 给统一通道 Provider 增加授权 EXISTS**

授权谓词使用 grantingRoleIds 关联：

```sql
and exists (
  select 1
  from wvp_role_resource_group rrg
  join wvp_resource_group_closure rgc
    on rgc.ancestor_id = rrg.resource_group_id
  join wvp_resource_group_item rgi
    on rgi.group_id = rgc.descendant_id
  where rrg.role_id in
  <foreach collection="grantingRoleIds" item="roleId" open="(" separator="," close=")">
    #{roleId}
  </foreach>
    and (
      (rgi.resource_type = 'CHANNEL' and rgi.resource_id = dc.id)
      or (rgi.resource_type = 'GB_DEVICE'
          and dc.data_type = 1 and rgi.resource_id = dc.data_device_id)
      or (rgi.resource_type = 'STREAM_PUSH'
          and dc.data_type = 2 and rgi.resource_id = dc.data_device_id)
      or (rgi.resource_type = 'STREAM_PROXY'
          and dc.data_type = 3 and rgi.resource_id = dc.data_device_id)
    )
)
```

ALL 不增加谓词；NONE 增加 `and 1=0`。Provider 测试断言所有 role ID 使用 `#{}` 绑定。

- [x] **Step 3: 区分 HTTP 查询和内部查询**

当前 `selectAll()` 被定时任务和 HTTP 共用的地方拆成两个命名方法。HTTP Controller 必须从 AuthorizationFacade 获取 scope；内部调用必须经过 InternalPrincipal/内部 service，不允许业务请求传 `all=true`。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=VideoListScopeProviderTest,DeviceChannelProviderTest,StreamProxyProviderTest test`

Expected: 原 SQL Provider 测试继续 PASS，新增范围用例 PASS。

实际结果：定向回归 59 项全部通过；后端全量回归 298 项、0 失败、0 错误、1 项既有跳过。按用户要求未执行 `git add` 或 `git commit`。

---

### Task 7: 过滤 JT1078、录像、告警、位置、统计和导出

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/dao/JTTerminalMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/dao/JTChannelMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/CloudRecordServiceMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/AlarmMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/CloudRecordServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/AlarmServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/MobilePositionServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/migration/CloudRecordSourceBackfillService.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/DerivedResourceScopeTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/migration/CloudRecordSourceBackfillServiceTest.java`

**Interfaces:**
- Produces: 录像、告警和位置通过内部 channelId 继承权限，不写资源组 item。
- Produces: 无法回填录像为 LEGACY_UNBOUND，仅 systemUser 可见。

> **完成记录（2026-07-15）：已验收。** JT1078 终端/通道、告警、位置、云录像派生列表、日期、URL、ZIP 任务、首页统计和导出均在数据库层复用同一 scope；搜索、时间范围和分页不会绕过授权条件，内部维护/播放路径使用独立查询。新录像通过 `CloudRecordMetadataService` 在同一事务固化来源；旧录像按 `call_id -> app+stream 当前/历史会话 -> 统一通道映射` 唯一回填，零/多候选记录为带 `source_reason` 的 `LEGACY_UNBOUND`。定向测试及 324 项后端全量回归通过。

- [x] **Step 1: 写派生资源失败测试**

覆盖 JT_TERMINAL 动态包含新 JT_CHANNEL、JT_CHANNEL 精细授权、告警 channel 解析、设备录像、云录像、轨迹、统计 count 和导出。搜索或时间范围不能绕过资源条件。

- [x] **Step 2: 新录像写入时固化来源**

CloudRecord 写入同时保存 `source_type/source_id/channel_id`。失败时录像元数据事务整体回滚；不能创建缺失来源的新记录。

- [x] **Step 3: 回填旧录像**

回填优先级固定为 `call_id -> app+stream 当前/历史会话 -> 统一通道映射`。唯一匹配才写 channel_id；零个或多个候选均写 LEGACY_UNBOUND 并记录原因，不猜测绑定。

- [x] **Step 4: 数据库过滤派生列表**

录像/告警 SQL 先 JOIN 到统一通道，再复用授权 EXISTS。Legacy 记录增加 `systemUser=true` 条件；普通用户即使拥有 cloud-record:list 也不可见。

- [x] **Step 5: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=DerivedResourceScopeTest,CloudRecordSourceBackfillServiceTest test`

Expected: PASS，统计/分页/导出结果与列表授权集合一致。

实际结果：`DerivedResourceScopeTest`、`DerivedResourceScopeMapperTest`、`CloudRecordSourceBindingResolverTest`、`CloudRecordMetadataServiceTest`、`CloudRecordSourceBackfillServiceTest`、`VideoListScopeMapperTest` 和 `VideoResourceSchemaH2Test` 等定向测试通过；后端全量回归 324 项、0 失败、0 错误、1 项既有跳过。按用户要求未执行 `git add` 或 `git commit`。

---

### Task 8: 封堵 GB28181 点播、停止、PTZ、广播和对讲 IDOR

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlayController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlaybackController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PtzController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/ChannelController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/ChannelFrontEndController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/GBRecordController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/MediaObjectAuthorizationMvcTest.java`

**Interfaces:**
- Consumes: `AuthorizationFacade.requireVideoResource(String permissionCode, VideoResourceType resourceType, long resourceId)`。
- Produces: SIP INVITE/PTZ/录像命令发出前已完成内部通道对象授权。

- [x] **Step 1: 写“业务命令未执行”失败测试**

替换 deviceId/channelId 为无权对象时，验证 403/404 且 mock commander、playService、inviteStreamService 从未被调用。合法对象只调用一次。

- [x] **Step 2: 在业务动作前解析和授权**

```java
VideoResourceRef channel = channelResourceResolver.resolveGbChannel(deviceId, channelId);
authorizationFacade.requireVideoResource(
        "video:channel:play", channel.type(), channel.id());
return playService.play(mediaServerId, deviceId, channelId, null, null, null);
```

停止播放同样要求 `video:channel:stop`；PTZ 基础控制用 `video:ptz:control`，预置位用 `video:ptz:preset`，巡航用 `video:ptz:cruise`；广播/对讲使用各自权限。

- [x] **Step 3: 覆盖兼容 API**

`web/gb28181/ApiControlController`、`ApiStreamController`、`web/custom/CameraChannelController` 必须调用同一 resolver/facade，不能因为旧接口或第三方接口跳过对象授权。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=MediaObjectAuthorizationMvcTest test`

Expected: 每类命令有允许、无功能权限、无资源和不存在四种结果；拒绝时业务 mock 零调用。

实际结果：`MediaObjectAuthorizationMvcTest`、`MediaObjectFunctionalPermissionMvcTest`、`GbChannelAccessAuthorizerTest`、`ResourceResolverRegistryTest`、`VideoResourceLookupMapperTest` 共 47 项定向测试通过，0 失败、0 错误；覆盖允许、缺功能权限、无资源和不存在四类结果，拒绝路径业务 mock 零调用，并覆盖外部 GB 通道、统一兼容通道、内部通道和 RTP 流解析。测试同时复现并修复了私有兼容 Handler 绕过 Method Security 的问题，`EndpointClassificationTest` 已增加受保护 MVC Handler 必须为 `public` 的门禁。后端全量回归覆盖 73 个测试套件、361 项测试，0 失败、0 错误、1 项既有跳过；438 个 HTTP Handler 与权限矩阵双向一致。用户侧业务验证进行中；按用户要求不执行下面的提交命令。

```bash
git add src/main/java/com/genersoft/iot/vmp/gb28181/controller src/main/java/com/genersoft/iot/vmp/web src/test/java/com/genersoft/iot/vmp/authorization/MediaObjectAuthorizationMvcTest.java
git commit -m "feat(authz): enforce GB28181 object authorization"
```

---

### Task 9: 封堵 JT1078、推流和拉流对象操作

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078Controller.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078TerminalController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/controller/StreamProxyController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/controller/StreamPushController.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoResourceLifecycleService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoResourceMigrationIssueRecorder.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/VideoResourceMigrationRequiredException.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/impl/VideoResourceLifecycleServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/authorization/video/impl/DefaultVideoResourceMigrationIssueRecorder.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/authorization/resourcegroup/ResourceGroupItemMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/service/impl/DeviceServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/service/impl/DeviceChannelServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/service/impl/GbChannelServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/service/impl/jt1078ServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/dao/JTTerminalMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/dao/JTChannelMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/service/impl/StreamProxyServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/service/impl/StreamPushServiceImpl.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/JtAndStreamObjectAuthorizationTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/JtAndStreamObjectAuthorizationMvcTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/JtAndStreamFunctionalPermissionMvcTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoResourceLifecycleServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoResourceDeletionCleanupTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/DeviceChannelGrantLifecycleTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoResourceMigrationIssueRecorderTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/VideoResourceMigrationManualHandlingTransactionTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/video/GbDeviceResourceDeletionCleanupTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/jt1078/dao/JTTerminalStableIdMapperTest.java`

**Interfaces:**
- Produces: JT play/talk/PTZ/record/control 和 proxy/push play/stop/edit/remove 全部对象授权。

- [x] **Step 1: 写 ID 替换和 app/stream 替换测试**

验证 terminalId、JT channel、proxyId、pushId、app/stream 任一替换均被拒绝；拒绝时不调用媒体或 JT 命令服务。

- [x] **Step 2: 使用内部主键授权**

外部 phoneNumber/channelId 先由 JT resolver 转换成 JT_CHANNEL 内部 ID。proxy/push 先按 id 查询；仅接收 app/stream 的旧接口必须解析唯一来源后授权，解析歧义时拒绝。

- [x] **Step 3: 删除时清理资源项**

删除终端、proxy 或 push 的同一业务事务中删除相应 resource_group_item；通道同步产生新 ID 时按稳定来源迁移显式授权，无法唯一迁移则停止并记录人工处理项，不能扩大到来源设备。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=JtAndStreamObjectAuthorizationTest,JtAndStreamObjectAuthorizationMvcTest,JtAndStreamFunctionalPermissionMvcTest,VideoResourceLifecycleServiceTest,VideoResourceMigrationIssueRecorderTest,VideoResourceMigrationManualHandlingTransactionTest,VideoResourceDeletionCleanupTest,DeviceChannelGrantLifecycleTest,GbDeviceResourceDeletionCleanupTest,JTTerminalStableIdMapperTest,ResourceResolverRegistryTest,VideoResourceLookupMapperTest,JtResourceAccessAuthorizerTest,StreamResourceAccessAuthorizerTest,GbChannelAccessAuthorizerTest,SecurityStatusCodeTest,EndpointClassificationTest test`

Run: `mvn test`

Expected: 组合定向门禁和后端全量回归均 PASS；所有拒绝路径业务副作用为零，端点矩阵双向一致。

完成记录（2026-07-15）：`JtAndStreamObjectAuthorizationTest` 的 11 项直接测试、两类 MVC 门禁的 14 项测试，以及解析器、授权适配器、稳定 ID、删除事务和迁移人工项测试共同组成 17 个测试类、75 项组合定向门禁，结果为 0 失败、0 错误、0 跳过。`VideoResourceLifecycleService` 会在同一业务事务清理 GB 设备/通道、JT 终端/通道、proxy/push 资源关系并刷新受影响用户授权版本；通道内部 ID 变化时只按同类型稳定来源唯一迁移，零候选或多候选停止迁移，并以独立事务写入人工处理审计项。后端全量回归覆盖 85 个测试套件、411 项测试，0 失败、0 错误、1 项既有跳过；438 个 HTTP Handler 与权限矩阵双向一致。只接收服务器路径的 `/api/jt1078/playback/download` 不在本任务冒充已授权，按 Task 10 改为记录 ID/受控路径后再封堵。

```bash
git add src/main/java/com/genersoft/iot/vmp/jt1078 src/main/java/com/genersoft/iot/vmp/streamProxy src/main/java/com/genersoft/iot/vmp/streamPush src/test/java/com/genersoft/iot/vmp/authorization
git commit -m "feat(authz): secure JT and stream object operations"
```

---

### Task 10: 封堵录像、告警、快照、下载、地图和批量操作

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/cloudRecord/CloudRecordController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/alarm/AlarmController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceQuery.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/MobilePositionController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/recordPlan/RecordPlanController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/authorization/DerivedObjectAuthorizationMvcTest.java`

**Interfaces:**
- Produces: recordId/alarmId/fileId/批量 ID 在读取路径或执行删除前授权。
- Defers: 短期下载票据本身由计划 4 完成，本任务先禁止匿名与任意路径。

- [x] **Step 1: 写派生对象和路径替换失败测试**

覆盖 recordId、alarmId、snapshot ID、计划关联通道、地图通道和批量删除。客户端提交服务器绝对路径、`..` 或替换文件名时返回 400/404；无权 ID 返回 404。

- [x] **Step 2: 先按记录 ID 找来源，再授权，再取路径**

客户端不再提交任意路径。CloudRecordController 接收 recordId，RecordResourceResolver 返回 channel，再调用 `video:cloud-record:download/remove/play`。Alarm snapshot 同理。

- [x] **Step 3: 批量全部验证**

```java
List<VideoResourceRef> resources = ids.stream()
        .map(recordResourceResolver::resolveRecord)
        .toList();
authorizationFacade.requireAllVideoResources(
        "video:cloud-record:remove", resources);
cloudRecordService.deleteAll(ids);
```

不得 catch 授权异常后继续处理有权子集。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `mvn -Dtest=DerivedObjectAuthorizationMvcTest test`

Expected: PASS，路径穿越、IDOR 和部分批量成功均不存在。

完成记录（2026-07-15）：录像、告警、快照、位置、录像计划关联、批量操作和 JT1078 受控 `downloadId` 均已在派生对象读取、路径输出或业务副作用前完成授权；缺失/畸形参数统一返回 400，批量输入必须全量通过授权。JT1078 下载在 FTP 可用性检查前先解析 UUID 并按原终端/通道授权，避免提前泄露服务配置。地图轨迹页改用 Axios `params` 传递真实 `channelId` 和时间范围，请求失败会恢复加载态并保留对话框供重试。`DerivedObjectAuthorizationMvcTest` 21 项通过，Task 10 的 14 个组合定向测试类共 108 项通过；H2 基线和视频资源 Schema 契约测试同时通过。后端全量回归覆盖 88 个测试套件、451 项测试，0 失败、0 错误、1 项既有跳过，438 个 HTTP Handler 与权限矩阵双向一致。前端全量回归覆盖 18 个测试套件、62 项测试，地图新增 2 项行为测试通过，ESLint 为 0 error（保留 679 条既有 warning）。本任务已完成自动化验收；短期下载票据仍按边界留给计划 4。

---

### Task 11: 实现视频资源组前端和业务动作权限

**Files:**
- Create: `web/src/api/system/resourceGroup.js`
- Create: `web/src/views/system/resourceGroup/index.vue`
- Create: `web/src/views/system/resourceGroup/components/ResourceGroupTree.vue`
- Create: `web/src/views/system/resourceGroup/components/ResourceItemDialog.vue`
- Modify: `web/src/views/system/role/components/RoleForm.vue`
- Modify: `web/src/views/device/index.vue`
- Modify: `web/src/views/channel/index.vue`
- Modify: `web/src/views/live/index.vue`
- Modify: `web/src/views/jtDevice/index.vue`
- Modify: `web/src/views/streamProxy/index.vue`
- Modify: `web/src/views/streamPush/index.vue`
- Modify: `web/src/views/cloudRecord/index.vue`
- Modify: `web/src/views/alarm/index.vue`
- Modify: `web/src/views/common/playerTabs.vue`
- Test: `web/tests/unit/views/system/resourceGroup/index.spec.js`
- Test: `web/tests/unit/permissions/mediaActions.spec.js`

**Interfaces:**
- Consumes: `/api/system/resource-group` 和当前用户 permissions。
- Produces: 角色表单的视频范围 ALL/CUSTOM/NONE 和资源组树。

- [x] **Step 1: 写管理页失败测试**

测试资源组懒加载、父组授权展示、整台设备提示、单通道选择、重复项去重、停用组、无 assign 权限禁用角色资源保存。

- [x] **Step 2: 实现独立授权树**

可参考 `web/src/views/common/GroupTree.vue` 的交互，但 API、store 和节点类型完全独立。页面不能调用 `/api/group`、`/api/region`，也不能修改 GB 业务分组。

- [x] **Step 3: 覆盖所有操作入口**

普通按钮用 `v-hasPermi`；右键菜单、播放器工具栏、动态下拉使用 `$auth.hasPermi()`。至少覆盖 play/stop/talk/PTZ/record/download/remove/proxy/push/JT control/share。前端不保存资源 ID 权限集合，后端 404/403 仍需正常处理。

- [x] **Step 4: 运行测试并完成验收（按当前要求不提交）**

Run: `cd web && npm run test:unit -- --runInBand tests/unit/views/system/resourceGroup/index.spec.js tests/unit/permissions/mediaActions.spec.js`

Expected: 2 个 suite PASS。

Run: `cd web && npm run lint`

Expected: 退出码 0。

完成记录（2026-07-15）：独立资源组页面、角色视频范围和业务动作入口门禁已经落地。前端在操作者没有 `system:resource-group:assign` 时会提交原视频范围；后端在角色事务中锁定当前记录，只在范围实际变化时要求分配权限，直接篡改请求会返回 403。播放地址分享、PTZ 控制/预置位/巡航、JT 录像和多媒体下载均同时具备展示门禁与方法级校验。资源组与业务动作两个定向套件共 34 项测试通过，角色、菜单和迁移契约的后端定向测试 41 项通过；前端全量 20 个套件、96 项测试全部通过，ESLint 为 0 error（保留 680 条 warning）；后端全量 88 个套件、456 项测试，0 失败、0 错误、1 项既有跳过，438 个 HTTP Handler 与权限矩阵双向一致。Task 11 已完成自动化验收，用户侧业务验证仍在进行。按用户要求不执行 `git add`、`git commit` 或 `git push`。

---

### Task 12: 扩大端点门禁并完成阶段 4～5 验收

> 完成记录（2026-07-15）：GB 设备/通道、二级统一通道、`/api/sy` 自定义摄像头、平台共享通道、RTP/PS 发流、媒体信息、快照、活动 SSRC、云录像合并任务及 DeviceControl SIP 指令均已接入 SQL 范围或对象门禁。438 个 HTTP Handler 已全部分类，其中 286 个 `REQUIRE_RESOURCE` 端点只引用 `ChannelResourceResolver`、`StreamResourceResolver`、`RecordResourceResolver`、`AlarmResourceResolver` 4 类真实解析器；独立视频资源授权矩阵已建立并由机器测试与 CSV 双向校验。7 个定向测试类共 94 项通过，静态旁路检查通过。旧 `ChannelProviderTest` 已补充强制 `VideoScopeQuery` 夹具，后端全量 91 个测试套件、497 项测试全部通过（1 项既有跳过）；前端 20 个测试套件、96 项全部通过，ESLint 0 error（保留 680 条 warning）。Task 12 已完成自动化验收，用户侧业务验证进行中。

**Files:**
- Modify: `src/test/java/com/genersoft/iot/vmp/auth/EndpointClassificationTest.java`
- Modify: `docs/security/endpoint-permission-matrix.md`
- Create: `docs/security/video-resource-authorization-matrix.md`
- Create: `src/test/java/com/genersoft/iot/vmp/authorization/VideoAuthorizationFlowTest.java`

**Interfaces:**
- Produces: 所有 HTTP Handler 恰有一种分类；RequireResource 项不再是 LEGACY_AUDIT。
- Produces: 计划 4 可依赖 `MediaStreamBinding` 的资源授权入口。

- [x] **Step 1: 把分类扫描扩大到全仓**

反射扫描 RequestMappingHandlerMapping，不硬编码当前映射数量。每个映射必须恰好属于 Public、Internal、MediaHook、RequirePermission 或 RequireResource；矩阵中的 handler 和实际映射双向一致，失效行和新增漏标都失败。

- [x] **Step 2: 完成资源授权矩阵**

每行写 Method/Path/Permission/Resolver/DenialStatus/ListScope/BatchPolicy。对所有接收 deviceId、channelId、JT ID、proxyId、pushId、recordId、alarmId、app/stream、fileId 和批量 ID 的端点逐一列明 resolver。

- [x] **Step 3: 执行后端验收**

Run: `mvn -Dtest=VideoAuthorizationFlowTest,MediaObjectAuthorizationMvcTest,DerivedObjectAuthorizationMvcTest,JtAndStreamObjectAuthorizationTest test`

Expected: 测试数大于 0 且全部 PASS。

Run: `mvn test`

Expected: 全仓单测 PASS，Surefire 汇总测试数大于 0。

执行记录（2026-07-15）：扩展后的 7 个定向测试类共 94 项全部通过。`ChannelProviderTest` 的旧参数夹具已补充 `/api/sy` SQL fail-closed 强制要求的 `VideoScopeQuery`，该测试类 17 项通过；后端全量覆盖 91 个测试套件、497 项测试，0 失败、0 错误、1 项既有跳过。

- [x] **Step 4: 静态检查旁路**

```bash
rg "roleId\s*==\s*1|roleId\s*!=\s*1|getRole\(\)\.getId\(\)" src/main/java
rg "DepartmentScope|dept_id" src/main/java/com/genersoft/iot/vmp/authorization/video
rg "selectAll\(" src/main/java/com/genersoft/iot/vmp --glob '*.java'
```

Expected: 无固定角色 ID；视频授权包不依赖部门；每个无 scope 的 selectAll 调用都有明确 InternalMaintenance 用途或已替换。

执行记录（2026-07-15）：未发现固定角色 ID 或视频授权包对 `DepartmentScope`/`dept_id` 的依赖；推流、拉流、JT 通道列表的 `selectAll` 均显式携带 `VideoScopeQuery`，其余无 scope 查询属于角色/用户系统管理路径。CSV 和视频资源矩阵中无 `LEGACY_AUDIT` 或虚构解析器。

- [x] **Step 5: 提交（按用户要求不执行）**

```bash
git add src/test docs/security
git commit -m "test(authz): close video authorization coverage"
```

以上命令仅保留原计划的变更边界；本次未暂存、未提交、未推送任何文件。

## 完成定义

- 角色可以配置 ALL/CUSTOM/NONE 视频范围并绑定独立资源组。
- GB_DEVICE/JT_TERMINAL 等来源授权动态覆盖新通道；CHANNEL/JT_CHANNEL 保持精细授权。
- 同一权限只合并真正授予该权限的角色资源，不发生跨角色串用。
- 设备、通道、JT、推流、拉流、录像、告警、位置、统计和导出均在数据库过滤。
- 点播、停止、PTZ、对讲、录像、告警、快照和批量操作在副作用前完成对象授权。
- 兼容 API 与新 API 使用同一 resolver/facade。
- 资源组前端与 GB 行政区划/业务分组彻底分离。
- 全仓端点矩阵无空分类；媒体原始 URL 封口和短票据由计划 4 完成。
