# WVP RBAC 完整改造实施总索引

> 状态：计划 1、计划 2 已完成并通过阶段验收；计划 3 的 Task 1～12 已完成自动化阶段验收，用户侧业务验证进行中；计划 4 的 Task 1～4 已完成，当前实施 Task 5
> 日期：2026-07-13
> 最新核对：2026-07-15
> 基线：wvp-pro 2.7.4 / Java 21 / Spring Boot 3.4.4 / Vue 2

## 0. 实施进度

| 计划 | 状态 | 当前证据 |
|---|---|---|
| 计划 1：认证硬化与功能 RBAC | 已完成 | `mvn test`：164 项、0 失败、1 项既有跳过；Jest：14 个 suite、47 项全部通过；ESLint：0 error；402 个 HTTP Handler 与权限矩阵双向一致 |
| 计划 2：部门数据范围与系统管理前端 | 已完成 | `mvn test`：243 项、0 失败、1 项既有跳过；Jest：17 个 suite、60 项全部通过；ESLint：0 error；生产构建成功；430 个 HTTP Handler 与权限矩阵双向一致 |
| 计划 3：视频资源组、查询过滤与对象级授权 | 自动化验收完成 | Task 1～12 已完成。Task 12 覆盖设备/通道、平台共享、自定义摄像头、RTP/PS、媒体信息、快照、活动 SSRC、云录像合并任务和 DeviceControl 等对象门禁，并完成 438 个 HTTP Handler 分类清零及 286 行独立视频资源授权矩阵；7 个定向测试类共 94 项通过。旧 `ChannelProviderTest` 已补充强制 scope 夹具，后端全量 91 个测试套件、497 项测试，0 失败、0 错误、1 项既有跳过；前端 20 个测试套件、96 项通过，ESLint 0 error（保留 680 条 warning）。用户侧业务验证进行中 |
| 计划 4：媒体票据、API Key scope、多数据库与灰度 | 实施中 | Task 1～4 已完成 API Key scope、播放票据、长 URL 收口和逐节点 Hook 认证；当前实施 Task 5 `on_play` 票据强制校验 |

按用户要求，实施过程不创建 Git commit；各计划中的提交步骤仅作为原始变更边界参考，不执行。

## 1. 文档关系

设计规格说明“为什么这样设计、边界在哪里”；四份实施计划说明“按什么顺序改哪些文件、先写什么测试、怎样验收和提交”。

- [总体设计](../specs/2026-07-13-wvp-rbac-dept-resource-design.md)
- [计划 1：认证硬化与功能 RBAC](2026-07-13-wvp-authentication-functional-rbac.md)
- [计划 2：部门数据范围与系统管理前端](2026-07-13-wvp-department-data-scope.md)
- [计划 3：视频资源组、查询过滤与对象级授权](2026-07-13-wvp-video-resource-authorization.md)
- [计划 4：媒体票据、API Key scope、多数据库与灰度](2026-07-13-wvp-media-access-api-key-rollout.md)

四份计划不是四个可以任意并行的项目。后面的计划依赖前面的稳定接口，执行顺序固定：

~~~mermaid
flowchart LR
    P1["计划 1：身份与功能权限"] --> P2["计划 2：部门数据范围"]
    P1 --> P3["计划 3：视频资源授权"]
    P2 --> P3
    P3 --> P4["计划 4：媒体封口与灰度"]
~~~

计划 2 的前端页面可以在计划 1 后半段并行开发；计划 3 的资源组管理页面可以在视频授权核心接口稳定后并行开发。数据库表名、权限编码、主体模型、范围合并规则和媒体票据协议不能由并行任务各自修改。

## 2. 四份计划的交付边界

| 计划 | 任务数 | 主要交付 | 结束时授权模式 |
|---|---:|---|---|
| 计划 1 | 12 | 测试门禁、登录、JWT、多角色、菜单、功能权限、动态路由、API Key P0 | audit |
| 计划 2 | 9 | 部门 closure、六类范围、用户/部门/他人 Key/审计过滤、系统管理页 | audit |
| 计划 3 | 12 | 独立资源组、来源/通道授权、列表过滤、对象级 IDOR 封堵 | audit，测试用 enforce |
| 计划 4 | 11 | Key scope、播放/下载票据、Hook、分享、WebSocket、四库验证、enforce/contract | enforce |

合计 44 个可独立评审和提交的任务。每个任务都有自己的失败测试、最小实现、定向测试、预期结果和提交边界。

## 3. 必须稳定的跨计划接口

### 3.1 当前主体

计划 1 产出，后续只能消费，不得另建一套登录用户模型：

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
        Set<String> permissions) implements Principal {}
```

`id` 表示当前主体自身 ID；用户主体时等于 userId，API Key 主体时是 apiKeyId。password、raw API Key、JWT、媒体 ticket 不允许进入主体。

### 3.2 功能权限

```java
public interface PermissionService {
    Set<String> resolvePermissions(long userId, long tokenVersion);
    Set<Long> findGrantingRoleIds(long userId, String permissionCode);
    boolean hasPermission(AuthPrincipal principal, String permissionCode);
}
```

`findGrantingRoleIds` 是部门范围和视频范围不串用的关键：只计算真正授予当前权限的角色。

方法权限使用：

```java
@PreAuthorize("@authz.check('video:channel:play')")
```

`PermissionEnforcer` 在 audit 记录真实拒绝但兼容放行，在 enforce 才实际阻断。直接 `hasAuthority` 会破坏灰度语义。

### 3.3 部门范围

```java
public record DepartmentScope(
        boolean all,
        Set<Long> deptIds,
        boolean includeSelf,
        long userId) {}

public interface DepartmentScopeService {
    DepartmentScope resolveDecisionScope(AuthPrincipal principal, String permissionCode);
    DepartmentScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode);
}
```

`includeSelf` 用于表达 SELF 与 CUSTOM/DEPT 的多角色并集。部门范围不能被视频业务 Mapper 引用。

### 3.4 视频资源范围

```java
public interface AuthorizationFacade {
    void requirePermission(String permissionCode);
    void requireVideoResource(
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId);
    void requireVideoResource(
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId,
            ResourceDenialMode denialMode);
    void requireAllVideoResources(
            String permissionCode,
            Collection<VideoResourceRef> resources);
    VideoScope resolveVideoScope(String permissionCode);
    VideoScopeQuery resolveVideoScopeQuery(String permissionCode);
    DepartmentScope resolveDepartmentScope(String permissionCode);
}
```

Controller 不读取角色、部门或资源组 Mapper。外部设备编号、通道编号、app/stream、recordId 和 alarmId 先由 resolver 转为内部主键，再进入门面。

### 3.5 媒体绑定和票据

```java
public record MediaStreamBinding(
        String mediaServerId,
        String app,
        String stream,
        VideoResourceType resourceType,
        long resourceId) {}

public record MediaPlayHookRequest(
        String mediaServerId,
        String app,
        String stream,
        String schema,
        String ticket) {}
```

计划 4 不直接查询角色或资源组表，只通过计划 3 的门面确认资源，并通过 BindingResolver 确认真实节点和流。

## 4. 已锁定的实施细节

### 4.1 audit 不等于“先加注解再说”

普通 `@PreAuthorize("hasAuthority('video:channel:play')")` 会立即返回 403，无法做到只记录不阻断。因此功能权限、部门范围和视频范围都区分 decision 与 effective：

| 模式 | decision | effective |
|---|---|---|
| disabled | 仍可计算；只允许开发/测试 | 兼容旧行为 |
| audit | 真实 ALLOW/DENY 并写审计 | 旧业务兼容放行/全量 |
| enforce | 真实 ALLOW/DENY | 使用真实权限和范围 |

新增角色、菜单、部门、资源组等授权配置接口必须 always-enforce，不能让 audit 变成篡改权限配置的窗口。

### 4.2 数据库使用 expand-contract

旧库的 `wvp_user_role` 实际是角色主表。直接把它改名，再创建同名关系表，会让 2.7.4 应用无法回滚。实施采用：

```text
expand：保留旧 wvp_user_role
        新建 wvp_role
        新建 wvp_user_role_rel
        新后端双写旧 role_id 和新关系

contract：确认 enforce 稳定且不再回滚旧应用
          删除旧角色主表
          wvp_user_role_rel → wvp_user_role
```

contract 前可以回滚应用和 expand 结构；contract 后回退必须恢复数据库快照。

### 4.3 PostgreSQL 与 Kingbase 脚本分离

2.8.0 不再沿用合并文件，分别维护：

```text
数据库/2.8.0/初始化-postgresql-2.8.0.sql
数据库/2.8.0/更新-postgresql-2.8.0.sql
数据库/2.8.0/初始化-kingbase-2.8.0.sql
数据库/2.8.0/更新-kingbase-2.8.0.sql
```

两套库各自验证 sequence、注释、保留字、索引和事务 DDL，不能以 PostgreSQL 测试通过代替 Kingbase 验证。

### 4.4 API Key 分两阶段

计划 1 立即修复 P0：随机 secret、HMAC 哈希、明文一次显示、列表掩码、所有权和绝对过期时间。计划 4 增加 permission scope、resource-group scope、IP allowlist 和 LEGACY 清理。任何阶段都不允许继续新增明文 Key。

### 4.5 ABL schema 不猜测

ZLM on_play 直接提供 schema；ABL 只有 networkType。实施增加显式 `networkType -> schema` 配置，audit 收集实际值，未知映射在 enforce 拒绝。不能默认当成 FLV，也不能因为字段不完整直接放行。

## 5. 测试策略

规划时基线与当前状态：

- 仓库没有 Maven Wrapper，命令继续使用 `mvn`；
- 计划 1 Task 1 已删除 Surefire 3.2.5 中硬编码的 `skipTests=true`，当前 `mvn test` 会实际执行测试；
- 后端已从少量 JUnit 5 单元/SQL Provider 测试扩展到 88 个测试套件、456 项测试，仍按下述分层策略维护；
- 主启动类会带起 SIP、Redis、调度等组件，不适合把所有测试写成 `@SpringBootTest`；
- 前端继续使用 Jest 和 `npm run test:unit`，当前为 20 个测试套件、96 项测试。

测试分层固定为：

1. 纯单元测试：权限合并、票据、密码、scope、resolver；
2. SQL Provider 测试：绑定参数、空集合 deny、授权 EXISTS；
3. `@WebMvcTest`：401/403/404、方法权限、拒绝时业务 mock 零调用；
4. H2 结构契约：每阶段即时验证逻辑模型；
5. MySQL/PostgreSQL Testcontainers：计划 4 验证真实方言；
6. Kingbase 外部可销毁测试库：计划 4 使用独立 profile；
7. Jest：动态路由、指令、系统页面、播放器、分享和 API Key；
8. 静态扫描：端点分类、硬编码角色、URL token、长期媒体 URL、公开下载代理。

修复 Surefire 前唯一可信的定向形式是：

```bash
mvn -DskipTests=false -Dtest=EndpointClassificationTest test
```

Task 1 完成后使用计划中列明测试类的 `mvn -Dtest=EndpointClassificationTest test` 形式，阶段门禁使用 `mvn test` 和 `mvn verify`。每次都检查 Surefire 的 `Tests run` 大于 0，不能只看 BUILD SUCCESS。

## 6. 发布批次

| 批次 | 内容 | 数据库状态 | 回退方式 |
|---|---|---|---|
| R1 | 测试门禁、expand schema、认证兼容 | 旧表 + 新表并存 | 回旧应用，保留 expand 表 |
| R2 | JWT、多角色、菜单、动态路由、Key P0 | 双写 | 回 R1 |
| R3 | 部门范围和系统管理页 | 双写 | mode=audit，回 R2 |
| R4 | 资源组、列表过滤、对象检查 | 双写 | mode=audit，回 R3 |
| R5 | Hook 身份、票据双轨、分享/下载 | 双写 | 关闭票据强制，回 R4 |
| R6 | enforce、关闭长期 URL、停用 LEGACY Key | 双写 | 回 audit，不丢配置 |
| R7 | contract | 最终表名 | 恢复 contract 前快照 |

每个批次发布前保存端点矩阵、迁移核对结果、授权差异统计和回归测试报告。R6 至少观察一个完整业务周期；“完整业务周期”由实际值班、录像、告警和设备同步周期确定，不能用固定一小时替代。

## 7. 评审检查点

开始写代码前，建议按下面顺序评审：

1. 先确认计划 1 的包名、主体签名、权限编码和 expand 表名；
2. 确认 CUSTOM 部门只包含显式勾选部门；
3. 确认六类可绑定视频资源与现有内部主键映射；
4. 确认每类操作的 permissionCode，尤其 play/stop、record play/download/remove 是否分开；
5. 确认 ZLM/ABL 网络入口和 Hook secret 下发方式；
6. 确认旧前端、旧 API、旧 Key 和长期 URL 的 Sunset 时间；
7. 确认 Kingbase 可销毁测试库和 contract 前数据库快照流程。

这些检查点确认后，实施者无需再自行决定部门与摄像头关系、用户直授权资源、角色范围合并或 audit 语义。

## 8. 设计覆盖矩阵

这张表用于评审“完整性”，不是重复四份计划。设计规格中的每个主题都必须能落到明确任务和验收门禁。

| 设计规格主题 | 主要实施任务 | 最终验证位置 |
|---|---|---|
| 现状问题、认证边界、总体安全模型 | 计划 1 Task 1、3～7、10 | 计划 1 Task 12；计划 4 Task 11 |
| 用户、角色、菜单、多角色并集 | 计划 1 Task 2、6～8、11 | 计划 1 Task 12 |
| 部门只约束人员/管理数据 | 计划 2 Task 1～7 | 计划 2 Task 9 |
| 若依式用户、角色、部门、菜单页面 | 计划 1 Task 11；计划 2 Task 6、8 | 计划 2 Task 9 |
| 独立视频资源组、树、角色授权 | 计划 3 Task 1～5、11 | 计划 3 Task 12 |
| 设备、通道、JT1078、推拉流、录像、告警范围 | 计划 3 Task 3、6～10 | 计划 3 Task 12 |
| 列表过滤、对象级校验、404/403 策略 | 计划 3 Task 4～10 | 计划 3 Task 12 |
| API Key 安全、所有权、部门、细粒度 scope | 计划 1 Task 9；计划 2 Task 7；计划 4 Task 1 | 计划 4 Task 11 |
| ZLM/ABL Hook、短票据、流绑定 | 计划 4 Task 2～6 | 计划 4 Task 10～11 |
| 分享、下载、WebSocket、Nginx 边界 | 计划 4 Task 7～9 | 计划 4 Task 11 |
| 审计、audit/enforce、灰度与回滚 | 计划 1 Task 12；计划 2 Task 7；计划 3 Task 12；计划 4 Task 11 | R1～R7 发布证据 |
| MySQL、PostgreSQL、Kingbase、H2 与 expand-contract | 计划 1 Task 2；计划 2 Task 1；计划 3 Task 1；计划 4 Task 10～11 | 计划 4 Task 10～11 |

如果某次需求调整改变了表名、权限编码、范围并集、票据协议或灰度语义，先更新总体设计和本矩阵，再修改对应实施计划；不能只改代码。
