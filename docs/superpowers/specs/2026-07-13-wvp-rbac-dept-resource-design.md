# WVP RBAC、部门数据权限与视频资源授权改造设计

> 状态：整体方向已评审，实施细节持续补充
> 日期：2026-07-13
> 实施进度核对：2026-07-15；计划 1、计划 2 已完成，计划 3 的 Task 1～12 已完成自动化阶段验收，用户侧业务验证进行中；计划 4 的 Task 1～4 已完成 API Key 范围、播放票据、长期媒体 URL 收口和 ZLM/ABL 逐节点 Hook 认证，当前实施 Task 5。详细证据以实施总索引为准。
> 基线：wvp-pro 2.7.4，Java 21，Spring Boot 3.4.4，Vue 2
> 适用范围：当前仓库后端、web 前端、MySQL、PostgreSQL、Kingbase、H2 测试库及 ZLMediaKit/ABL 媒体节点集成

## 1. 文档目的

这次改造要解决的不是“给用户多加几个角色字段”，而是把现有只有登录校验的系统，改造成一套可配置、可审计、能约束具体视频资源的授权系统。

最终交付包含四部分：

1. 若依式用户、角色、部门、菜单和按钮权限；
2. 部门数据范围，用于约束用户、部门、API Key 和相关系统管理数据；
3. 独立的视频资源组授权，用于约束设备、通道、推拉流、录像、告警和控制操作；
4. 媒体播放票据、下载票据和 Hook 认证，防止用户绕过 WVP 直接访问 ZLMediaKit。

部门树与视频资源组树相互独立。部门描述人员组织关系，视频资源组描述摄像头和流资源的授权集合。任何视频操作都必须同时满足功能权限和资源权限。

## 2. 已确认的设计决策

本设计以以下决策为前提，后续实现不得自行改变：

- 用户可以拥有多个角色；
- 角色绑定菜单、按钮和 API 权限；
- 用户属于一个主部门；
- 角色配置部门数据范围；
- 部门数据范围不直接过滤摄像头；
- 视频资源使用独立的树形资源组管理；
- 视频资源组只授权给角色，不允许用户直接绑定视频资源；
- 个别用户需要特殊资源时，通过专用角色解决；
- 多角色结果取允许集合的并集；
- 资源范围必须与当前操作所需权限一起计算，不能把不同角色的权限和资源范围交叉拼接；
- 第一版不实现显式拒绝规则；
- 超级管理员拥有全部功能和全部资源，但仍记录审计日志；
- 前端菜单和按钮只负责展示，后端始终是授权结果的唯一可信来源；
- 本期不引入多租户。部门不是租户，资源组也不是租户。
- `authorization.mode=audit` 必须计算并记录真实拒绝结果，但对旧业务返回兼容放行；只有 `enforce` 才实际阻断；
- 功能权限注解统一调用模式感知的 `PermissionEnforcer`，不能直接使用会在 audit 阶段立即阻断的 `hasAuthority`；
- 数据库迁移采用 expand-contract，兼容阶段不得立即复用旧表名导致旧版本无法回滚；
- 新增主键和应用服务接口统一使用 long/Long，旧 int 接口只在兼容控制器做范围校验和转换。

## 3. 改造前系统审计（历史基线）

### 3.1 改造前认证存在，授权基本为空

设计启动时，仓库已经接入 Spring Security、JWT、用户、角色和 API Key，但实际只区分已登录与未登录：

- WebSecurityConfig 对非白名单请求仅要求 authenticated；
- LoginUser.getAuthorities() 返回 null；
- JwtAuthenticationFilter 创建 Authentication 时传入空权限集合；
- 全仓约有 38 个控制器、439 个映射接口，没有方法级授权注解；
- wvp_user 通过 role_id 绑定单个角色；
- wvp_user_role 实际是角色主表，不是用户角色关系表；
- wvp_user_role.authority 已在控制器注释中标明“目前未使用”；
- 多处管理员判断直接比较 roleId == 1；
- 前端所有业务页面都在 constantRoutes 中，导航守卫只检查 token；
- Vuex 没有 roles、permissions、dept 或资源范围；
- 当前唯一的 authority 按钮判断没有对应 getter，实际不能形成权限控制。

### 3.2 改造时必须同步修复的安全问题

权限改造上线前必须处理以下问题，否则新 RBAC 会被旧入口绕过：

- interface-authentication 关闭后，过滤器会给请求设置一个已认证身份；
- JWT 可从 URL 查询参数读取，容易进入浏览器历史和代理日志；
- 会话策略使用 ALWAYS，与 JWT 场景不一致；
- CORS 未配置来源时允许任意 Origin 且携带凭据；
- 用户没有禁用、锁定、逻辑删除和 tokenVersion；
- JWT principal 和 Authentication credentials 携带数据库密码；
- 登出没有撤销 token，角色或密码变更后旧 token 仍可能继续使用；
- 登录支持 GET，前端把 MD5 值放入 URL；
- 用户实体和 API Key 实体可能直接序列化敏感字段；
- API Key 创建和列表缺少本人或管理员边界；
- API Key 过期时间存在毫秒时间戳与“未来分钟数”混用；
- API Key 明文保存和展示，且没有 permission scope；
- 设备快照、报警快照、录像下载和部分 JT1078 资源被匿名放行；
- 全部 ZLM/ABL Hook 路径公开，但没有独立的媒体节点身份校验；
- MediaServiceImpl 对部分 rtp 播放和缺少 callId 的流近似放行；
- 前端分享页把完整媒体 URL 放进查询参数，复制后可绕过 WVP；
- 日志 WebSocket 没有独立的读取权限。

这些问题按 P0 处理，不作为后续优化项。

## 4. 改造目标与非目标

### 4.1 目标

改造完成后应满足：

- 后端可以按稳定权限编码控制每个管理功能和危险操作；
- 前端根据后端返回的菜单树生成动态路由；
- 用户、角色、部门、菜单和资源组可以在管理页面维护；
- 角色可以配置部门数据范围和视频资源范围；
- 用户的多角色权限、部门范围和视频范围有明确合并规则；
- 所有列表、统计、导出和批量操作执行数据范围过滤；
- 所有按 ID、设备编号、通道编号、stream、recordId、alarmId 操作的接口执行对象级授权；
- 播放、PTZ、对讲、录像、下载、截图、告警和分享不能通过猜 ID 或复制 URL 绕过；
- API Key 权限是用户权限的受限子集；
- 权限变更可以在短时间内生效，旧 token、旧票据和缓存不会长期残留；
- MySQL、PostgreSQL、Kingbase 使用等价的数据模型，H2 测试库保持同步；
- 现有 GB28181、JT1078、推拉流和 ZLMediaKit 调度流程保持业务行为不变。

### 4.2 非目标

本次不包含：

- SaaS 多租户隔离；
- LDAP、AD、OIDC 或统一身份中心接入；
- 复杂的显式拒绝、条件策略语言和通用 ABAC 引擎；
- 摄像头固件、厂商账号或设备端口令托管改造；
- 把整个项目重构成微服务；
- 替换现有 Vue 2、Element UI、MyBatis 或 Redis；
- 重做 GB28181、JT1078 和媒体收流协议。

## 5. 术语

| 术语 | 含义 |
|---|---|
| 功能权限 | 是否允许执行某类操作，例如 video:channel:play |
| 部门范围 | 在用户、部门、API Key 和管理日志中可以看到哪些部门的数据 |
| 视频资源范围 | 可以访问哪些设备、通道、推流或拉流资源 |
| 资源组 | 独立于部门的树形视频资源授权集合 |
| 来源资源 | GB 设备、JT 终端、推流配置、拉流代理等产生通道的上级对象 |
| 通道资源 | wvp_device_channel 中可播放或可控制的统一通道 |
| 播放票据 | 绑定用户、资源、app、stream、媒体节点和有效期的短期凭证 |
| 权限版本 | 用户授权发生变化时递增的版本号，用于使缓存、token 和媒体票据失效 |
| 公开端点 | 不需要用户登录，但必须满足机器认证、短期签名或明确业务规则的端点 |

## 6. 总体架构

~~~mermaid
flowchart LR
    U["用户或 API Key"] --> A["认证层"]
    A --> P["当前主体 AuthPrincipal"]
    P --> F["功能权限服务"]
    P --> D["部门范围服务"]
    P --> R["视频资源授权服务"]
    F --> B["WVP 业务服务"]
    D --> B
    R --> B
    B --> M["媒体访问服务"]
    M --> Z["ZLMediaKit 或 ABL"]
    Z --> H["受保护的 Hook 回调"]
    H --> M
~~~

### 6.1 模块边界

改造不要求全仓迁移到新的分层架构，但权限核心必须从控制器和 Mapper 中抽离。新增边界如下：

#### Identity

负责登录、用户状态、部门、角色、JWT、API Key 和当前主体。

主要接口：

- CurrentPrincipalService；
- UserAuthenticationService；
- TokenVersionService；
- ApiKeyAuthenticationService。

#### Authorization

负责功能权限、部门范围、视频资源范围和对象级授权。

主要接口：

- PermissionService；
- DepartmentScopeService；
- VideoResourceScopeService；
- ResourceAuthorizationService；
- AuthorizationFacade。

#### Resource Catalog Adapter

把不同业务资源转换成统一授权标识，并解析继承关系。

主要接口：

- ResourceResolver；
- ChannelResourceResolver；
- RecordResourceResolver；
- AlarmResourceResolver；
- StreamResourceResolver。

#### Media Access

负责播放票据、分享、下载票据和媒体 Hook 认证。

主要接口：

- PlayTicketService；
- ShareService；
- DownloadTicketService；
- MediaHookAuthenticationService。

业务控制器只负责解析 HTTP 参数、调用应用服务和转换响应。它们不能自行拼权限 SQL，也不能直接根据角色 ID 决策。

### 6.2 依赖规则

- 业务控制器依赖 AuthorizationFacade，不依赖具体 Mapper；
- Authorization 核心依赖 Repository 接口，不依赖 Controller；
- 媒体票据服务通过 ResourceAuthorizationService 检查资源；
- ZLM/ABL Hook 适配器依赖 Media Access 接口；
- Mapper 只执行明确的查询条件，不读取一个“缺失时默认全量”的隐式 ThreadLocal；
- 定时任务、SIP 处理器和媒体 Hook 使用 SystemPrincipal 或内部服务入口，不伪装成人工用户请求。

## 7. 授权模型

### 7.1 基本判定

对需要具体资源的操作：

~~~text
允许 = 账号有效
    AND 凭证有效
    AND 拥有功能权限
    AND 拥有目标视频资源
~~~

对用户和部门管理操作：

~~~text
允许 = 账号有效
    AND 凭证有效
    AND 拥有功能权限
    AND 目标用户或部门落在有效部门范围内
~~~

纯系统配置操作，例如菜单定义、媒体节点配置和全局参数，只检查功能权限，默认不受部门范围影响。

### 7.2 权限与角色资源范围必须绑定

多角色合并不能简单地先合并所有权限、再合并所有资源。

示例：

- 角色 A 拥有 video:channel:play，资源组为一号楼；
- 角色 B 拥有 video:record:list，资源组为二号楼。

用户可以播放一号楼通道，也可以查询二号楼录像；用户不能用角色 A 的播放权限操作角色 B 的二号楼资源。

计算某个权限 P 的资源范围时，只考虑同时授予 P 的角色：

~~~text
有效角色 = 用户启用角色中授予权限 P 的角色

如果有效角色的 video_scope_type 为 ALL：
    资源范围 = 全部
否则：
    资源范围 = 所有有效角色关联资源组及其子组资源的并集
~~~

部门范围使用同样的“按权限选择角色”原则。

### 7.3 合并规则

- 多角色功能权限取并集；
- 同一权限下，多角色部门范围取并集；
- 同一权限下，多角色视频资源范围取并集；
- 任一有效角色为全部资源，则该权限下资源范围为全部；
- 被停用角色不参与计算；
- 被停用菜单或按钮权限不参与计算；
- 不实现显式拒绝；
- 超级管理员不依赖数据库中的星号权限或角色菜单关系展开；系统通过不可删除的 system_role 标识返回全部启用权限、菜单和资源；
- 超级管理员不再通过 userId == 1 或 roleId == 1 判断。

## 8. 权限编码

权限编码采用 domain:resource:action 格式。编码是稳定 API，不使用 URL 作为权限主键。

### 8.1 系统权限

| 模块 | 权限编码 |
|---|---|
| 用户 | system:user:list、add、edit、remove、resetPwd、changeStatus |
| 角色 | system:role:list、add、edit、remove、changeStatus、assign |
| 部门 | system:dept:list、add、edit、remove |
| 菜单 | system:menu:list、add、edit、remove |
| API Key | system:apikey:list、add、rotate、disable、remove、manageOthers |
| 视频资源组 | system:resource-group:list、add、edit、remove、assign |
| 操作日志 | operations:log:view、download |
| 平台状态 | operations:system:view |

### 8.2 视频业务权限

| 模块 | 权限编码 |
|---|---|
| GB 设备 | video:device:list、add、edit、remove、sync、config |
| 通道 | video:channel:list、edit、play、stop、talk、broadcast |
| PTZ | video:ptz:control、preset、cruise |
| 设备录像 | video:record:list、play、download、remove |
| 云端录像 | video:cloud-record:list、play、download、remove、collect |
| 告警 | video:alarm:list、remove、clear |
| 移动位置 | video:position:list、subscribe |
| 拉流代理 | video:proxy:list、add、edit、play、stop、remove |
| 推流 | video:push:list、add、edit、play、stop、remove |
| JT1078 | video:jt:list、edit、play、talk、ptz、record、control |
| 国标级联 | video:platform:list、manage、share-channel |
| 媒体节点 | video:media-server:list、manage |
| RTP/PS 工具 | video:rtp:manage、video:ps:manage |
| 分享 | video:stream:share、revoke-share |

实际落库时拆成完整字符串，例如 video:device:add，不能存储上表中的缩写。

### 8.3 权限矩阵要求

上线前生成一份 Controller 方法到权限编码的机器可检查清单。每个外部端点必须属于以下一种：

- RequirePermission：要求功能权限；
- RequireResource：要求功能权限和资源权限；
- PublicEndpoint：真正公开；
- InternalEndpoint：只允许可信内部调用；
- MediaHookEndpoint：只允许已认证媒体节点。

没有分类的新增端点在测试阶段直接失败，避免 439 个接口中遗漏一个即可越权。

## 9. 部门模型

### 9.1 部门职责

部门只描述人员组织关系，控制以下数据：

- 用户列表和用户详情；
- 用户新增、修改、停用、删除和密码重置；
- 部门树查询与维护；
- 下级用户的 API Key 管理；
- 按操作者部门查询操作日志；
- 以后新增且明确带有人员部门归属的系统数据。

部门不直接控制：

- GB28181 设备和通道；
- JT1078 终端和通道；
- 推流、拉流代理；
- 录像、告警和位置；
- 媒体节点；
- 国标行政区划和业务分组。

### 9.2 部门范围类型

| 值 | 含义 |
|---|---|
| ALL | 全部部门数据 |
| CUSTOM | 角色保存时明确勾选的部门集合 |
| DEPT | 当前用户主部门 |
| DEPT_AND_CHILD | 当前用户主部门及全部下级部门 |
| SELF | 只允许操作本人数据 |
| NONE | 无部门数据 |

对于用户管理，SELF 只能查看和修改本人允许修改的字段，不能为自己提升角色、部门或状态。

CUSTOM 第一版不隐式包含后代。前端可以启用“父子联动”，但保存时必须把当时勾选的每个部门 ID 明确写入 `wvp_role_dept`；以后新增的子部门不会自动扩大 CUSTOM 范围。需要动态包含当前主部门未来子部门时使用 DEPT_AND_CHILD。

### 9.3 部门树实现

采用 wvp_dept 和 wvp_dept_closure：

- wvp_dept 保存部门节点；
- wvp_dept_closure 保存祖先、后代和深度；
- 新增节点时写入自身关系和所有祖先关系；
- 移动节点时在事务中重建该子树闭包；
- 删除部门前必须确认没有用户和子部门，或由明确迁移操作先转移；
- 不采用 MySQL 专有 find_in_set；
- 不通过字符串拼接 dataScope SQL。

闭包表在 MySQL、PostgreSQL、Kingbase 和 H2 中都可用普通等值 JOIN。

## 10. 视频资源组模型

### 10.1 与现有目录分离

现有 wvp_common_group、行政区划、业务分组属于 GB28181 目录和展示模型，不能复用为授权资源组。

新资源组只服务授权：

~~~text
全部视频资源
├── 北京园区
│   ├── 一号楼
│   └── 二号楼
└── 上海园区
    ├── 仓库
    └── 办公区
~~~

资源组可以移动、停用和授权，但不改变摄像头向上级平台推送时的 GB 目录。

### 10.2 支持的资源类型

| 类型 | 主键来源 | 授权含义 |
|---|---|---|
| GB_DEVICE | wvp_device.id | 当前和以后同步出的全部通道 |
| CHANNEL | wvp_device_channel.id | 只授权指定统一通道 |
| JT_TERMINAL | wvp_jt_terminal.id | 当前和以后产生的 JT 通道 |
| JT_CHANNEL | wvp_jt_channel.id | 只授权指定 JT 通道 |
| STREAM_PROXY | wvp_stream_proxy.id | 该拉流代理及映射通道 |
| STREAM_PUSH | wvp_stream_push.id | 该推流及映射通道 |

播放和控制最终统一解析为 wvp_device_channel.id。来源资源授权负责自动覆盖当前和未来子通道；通道资源授权负责例外的精细范围。

### 10.3 资源组继承

资源组采用 wvp_resource_group_closure 保存树关系。

- 角色绑定父组时，自动拥有所有启用子组资源；
- 角色绑定子组不反向获得父组和兄弟组；
- 资源组停用后不参与新授权计算；
- 删除资源组前必须迁移资源项和角色关联；
- 一个资源可以属于多个资源组；
- 重复归属按集合去重；
- 来源设备同步出新通道后，无需给每个角色追加记录；
- 删除设备、终端、推拉流配置时同步清理对应资源项；
- 通道被重新同步为新数据库 ID 时，必须通过稳定来源关系迁移显式通道授权，不能静默放大为整台设备。

### 10.4 角色视频范围类型

| 值 | 含义 |
|---|---|
| ALL | 该角色授予的相关权限可以操作全部视频资源 |
| CUSTOM | 只允许角色关联资源组中的资源 |
| NONE | 该角色不贡献任何视频资源范围 |

角色绑定资源组时写入 wvp_role_resource_group。用户不直接绑定资源组。

### 10.5 派生数据权限

录像、告警、位置和媒体流不重复绑定资源组：

~~~text
录像或告警
  → 找到来源 channel_id
  → 解析统一通道
  → 检查用户对该通道的对应权限
~~~

必须补齐无法反向追溯的数据：

- wvp_cloud_record 增加 source_type、source_id、channel_id、source_reason；
- 新录像写入时在同一事务保存来源类型、来源对象和统一通道，来源缺失时不创建元数据；
- 旧录像按 `call_id -> app+stream 当前/历史会话 -> 统一通道映射` 的固定优先级回填并去重；
- 每一优先级只有唯一候选时才绑定，零候选或多候选均归入 LEGACY_UNBOUND，并在 source_reason 记录原因；
- LEGACY_UNBOUND 只允许超级管理员处理，不能默认全员可见，也不能因后续批次重复扫描而阻塞其他旧记录。

报警已经包含 channel_id，应统一使用中心通道数据库 ID。设备报警表中字符串 channel_id 需要通过设备和通道编号解析为中心通道。

## 11. 数据库设计

以下是逻辑字段。实际初始化和升级脚本分别为 MySQL、PostgreSQL 和 Kingbase 编写，H2 维护等价测试版本。

### 11.1 用户和角色

#### wvp_user

保留现有表并增加：

| 字段 | 类型建议 | 说明 |
|---|---|---|
| dept_id | bigint | 主部门 |
| nickname | varchar(64) | 显示名称 |
| status | smallint | 0 正常，1 停用 |
| locked | smallint | 0 未锁定，1 锁定 |
| deleted | smallint | 逻辑删除 |
| token_version | bigint | 权限和安全状态版本 |
| password_version | smallint | 旧 MD5 或 BCrypt |
| last_login_at | timestamp | 最近登录时间 |
| create_by/update_by | bigint | 审计字段 |

现有 role_id 保留一个兼容版本并双写主角色，下一大版本删除。

#### wvp_role

最终模型由现有 wvp_user_role 迁移并扩展。兼容发布阶段先新建 wvp_role 并从旧表复制，不立即删除或重命名旧表；阶段 7 停机 contract 后才移除旧角色主表并把临时关系表切换为最终名称：

| 字段 | 说明 |
|---|---|
| id/name | 角色 ID 和名称 |
| role_key | 稳定角色编码 |
| role_sort | 排序 |
| dept_data_scope | 部门数据范围 |
| video_scope_type | ALL、CUSTOM、NONE |
| status | 启用状态 |
| system_role | 是否系统内置、禁止删除 |
| create_by/create_time/update_by/update_time | 审计字段 |

旧 authority 不再作为授权依据，迁移完成后删除。

#### wvp_user_role

| 字段 | 说明 |
|---|---|
| user_id | 用户 ID |
| role_id | 角色 ID |

联合主键 user_id + role_id，并为 role_id 建索引。

expand 阶段物理表暂用 `wvp_user_role_rel`，新后端双写旧 `wvp_user.role_id` 和该关系表，旧后端仍读取旧 `wvp_user_role` 角色主表。contract 阶段确认不再回滚旧应用后，删除旧角色主表并把 `wvp_user_role_rel` 重命名为最终的 `wvp_user_role`。如果 contract 后需要回退，只能恢复 contract 前数据库快照，不能直接启动 2.7.4 应用。

### 11.2 部门

#### wvp_dept

包含 id、parent_id、dept_name、dept_code、order_num、leader_user_id、status、deleted 和审计字段。dept_code 唯一。

#### wvp_dept_closure

包含 ancestor_id、descendant_id、depth，联合主键 ancestor_id + descendant_id，并建立 descendant_id + ancestor_id 反向索引。

#### wvp_role_dept

包含 role_id、dept_id，联合主键。只对 CUSTOM 部门范围生效。

### 11.3 菜单和功能权限

#### wvp_menu

| 字段 | 说明 |
|---|---|
| id/parent_id | 菜单树 |
| menu_name | 名称 |
| menu_type | M 目录、C 菜单、F 按钮 |
| route_name/path/component | 前端路由信息 |
| perms | 稳定权限编码 |
| icon/order_num | 展示信息 |
| visible/status/cacheable/external | 路由状态 |
| system_menu | 是否内置 |
| 审计字段 | 创建和更新信息 |

F 类型必须有唯一 perms。M/C 可以没有 perms，但受父级或自身权限控制。

#### wvp_role_menu

包含 role_id、menu_id，联合主键，并为 menu_id 建索引。

### 11.4 视频资源组

#### wvp_resource_group

包含 id、parent_id、group_code、group_name、order_num、status、description 和审计字段。group_code 唯一。

#### wvp_resource_group_closure

包含 ancestor_id、descendant_id、depth，结构与部门闭包表相同。

#### wvp_resource_group_item

| 字段 | 说明 |
|---|---|
| id | 主键 |
| group_id | 资源组 |
| resource_type | GB_DEVICE、CHANNEL、JT_TERMINAL 等 |
| resource_id | 对应内部数据库主键 |
| create_by/create_time | 审计字段 |

唯一约束 group_id + resource_type + resource_id。泛型关系无法建立到所有业务表的物理外键，因此写入时必须由 ResourceResolver 校验资源真实存在，删除业务资源时执行清理任务，并定期扫描孤儿关系。

#### wvp_role_resource_group

包含 role_id、resource_group_id，联合主键。

### 11.5 API Key

重构 wvp_user_api_key：

| 字段 | 说明 |
|---|---|
| id/user_id | 主键和所属用户 |
| name | Key 名称 |
| key_hash | 使用服务端 pepper 的 HMAC-SHA-256 |
| key_prefix/key_last4 | 列表掩码 |
| expired_at | 绝对 UTC 毫秒时间戳 |
| status | 启用、停用、撤销 |
| ip_allowlist | 可选 CIDR 列表 |
| authz_version | 创建或轮换版本 |
| last_used_at | 最近使用 |
| create_time/update_time | 审计字段 |

新增：

- wvp_api_key_permission：api_key_id + menu_id，只允许关联带 perms 的启用菜单或按钮节点；
- wvp_api_key_resource_group：api_key_id + resource_group_id。

API Key 有效权限是用户当前权限与 Key scope 的交集；有效资源也是用户角色资源与 Key 资源组的交集。API Key 不能扩大用户权限。

### 11.6 媒体分享与审计

#### wvp_stream_share

保存 share_id、creator_user_id、resource_type、resource_id、expires_at、max_views、view_count、passcode_hash、status 和审计字段。分享页不保存原始媒体 URL。

#### wvp_authz_audit

保存 principal_type、principal_id、principal_dept_id、permission_code、resource_type、resource_id、decision、reason_code、request_id、client_ip、create_time。`principal_dept_id` 是操作发生时的部门快照，历史日志查询不能关联用户当前部门，否则用户调动后日志范围会漂移。拒绝日志不得记录密码、完整 token、完整 API Key 或媒体票据。

### 11.7 索引

至少建立：

- wvp_user(dept_id, status, deleted)；
- wvp_user_role(role_id, user_id)；
- wvp_role_menu(menu_id, role_id)；
- wvp_role_dept(dept_id, role_id)；
- 两张 closure 表的 descendant 反向索引；
- wvp_menu(parent_id, status, visible)、wvp_menu(perms)；
- wvp_resource_group_item(resource_type, resource_id)；
- wvp_role_resource_group(resource_group_id, role_id)；
- API Key 的 key_prefix、user_id、status、expired_at；
- wvp_cloud_record(channel_id, start_time)；
- wvp_authz_audit(principal_id, create_time)、resource_type + resource_id。

## 12. 身份认证设计

### 12.1 登录

- 只允许 POST JSON；
- 浏览器必须通过 HTTPS 发送原始密码；
- 后端使用 DelegatingPasswordEncoder，并注册只用于迁移的旧 MD5 编码器；
- 旧数据库无前缀的 32 位密码按 MD5 兼容；
- 用户使用原始密码成功登录后，立即升级为 BCrypt；
- 前端不再计算 MD5；
- 登录响应使用 LoginResponse，不返回 User、LoginUser 或 password；
- 连续失败达到阈值后短时锁定，并写审计日志；
- 停用、锁定和逻辑删除用户不能登录。

新 `/api/auth/login` 只接受原始密码。旧 `/api/user/login` 的 GET 立即返回 405；旧 POST 兼容默认关闭，如确需短期启用，只允许 `password_version=LEGACY` 用户提交“客户端 MD5 等于数据库旧 MD5”，添加 Deprecation/Sunset Header，且不得把这个结果伪装成 BCrypt 升级。只有新入口通过原始密码验证后才能安全升级密码。

### 12.2 JWT

JWT 只保存必要信息：

- userId；
- jti；
- tokenVersion；
- issuedAt；
- expiresAt；
- audience。

不保存密码和完整权限列表。请求到达后通过 userId + tokenVersion 获取 AuthPrincipal。

Token 只从 access-token Header 读取。URL token 兼容保留一个迁移版本，默认关闭并记录告警；WebSocket 使用独立握手认证。

用户 JWT 与 API Key 使用两条独立认证链。`JwtAuthenticationFilter` 不再读取 `api-key` Header；API Key 由独立过滤器生成 `PrincipalType.API_KEY` 主体，不能调用用户 JWT 的 verifyToken 兼容逻辑。

如果系统不再依赖 HTTP Session，安全策略改为 STATELESS。上线前通过测试确认 DeferredResult、WebSocket 和现有登出流程不依赖 Session。

### 12.3 权限版本和缓存

以下操作递增 user.token_version：

- 用户停用、锁定或删除；
- 密码修改；
- 用户角色变化；
- 角色权限、部门范围或视频资源范围变化；
- 用户主部门变化；
- 与用户相关的 API Key 安全状态变化。

角色发生变化时，找出受影响用户批量递增版本并删除 Redis 权限缓存。

缓存建议：

~~~text
auth:principal:{userId}:{tokenVersion}
auth:revoked:{jti}
auth:play-ticket:{jti}
~~~

缓存未命中时查数据库；缓存异常时不得默认放行。

第一版不引入未落库的 roleVersion、deptVersion 或 groupVersion。角色、部门和资源组变更通过找出受影响用户并递增 user.token_version 使主体缓存失效；结构缓存等出现明确性能证据后再增加对应版本字段。

退出登录把 jti 写入 `auth:revoked:{jti}`，TTL 为 token 剩余有效期。Redis 写入失败时不得返回虚假的退出成功：服务递增当前用户 token_version 使该用户全部会话失效；如果数据库更新也失败，则返回 503 并明确告知退出未完成。

### 12.4 401 与 403

- 未登录、token 无效、用户失效：HTTP 401；
- 已登录但功能权限不足：HTTP 403；
- 已登录但无权访问某个具体资源：对查询详情和下载可返回 404，避免确认资源存在；对明确控制命令可以返回 403；
- 全局异常处理不能把 AccessDeniedException 转成 500 或 HTTP 200；
- 前端收到 403 不退出登录，展示无权限页面或提示。

## 13. 部门数据范围执行

### 13.1 查询

DepartmentScopeService 接收当前主体和 permissionCode，返回：

~~~text
DepartmentScope {
    all: boolean
    deptIds: Set<Long>
    includeSelf: boolean
    userId: long
}
~~~

`includeSelf` 表示在部门集合之外额外包含本人，用于表达一个角色授予 SELF、另一个角色授予 CUSTOM/DEPT 时的并集。它不是“只允许本人”的互斥开关。NONE 表示 `all=false`、`deptIds` 为空且 `includeSelf=false`。

用户、部门、API Key 和操作日志查询显式接收该范围。Mapper 使用绑定参数 JOIN 或 IN，不接收拼接 SQL 字符串。

范围服务分别暴露决策范围和有效范围：

~~~java
DepartmentScope resolveDecisionScope(AuthPrincipal principal, String permissionCode);
DepartmentScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode);
~~~

`resolveDecisionScope` 永远返回真实计算结果并用于审计；`resolveEffectiveScope` 在 enforce 返回真实范围，在 audit 返回兼容旧行为的全量范围并记录“如果 enforce 将过滤什么”，在 disabled 仅允许开发/测试配置。新系统管理接口可以按发布批次显式标记为始终 enforce，但必须写入端点矩阵，不能靠控制器自行判断模式。

### 13.2 命令

修改、停用、删除和重置密码前执行对象级检查：

- 目标用户必须落在有效部门范围；
- 非超级管理员不能操作超级管理员；
- 用户不能为自己增加角色或扩大部门；
- 部门管理员不能把用户移动到自己范围外；
- 删除部门前验证子部门和用户；
- 修改角色、菜单和全局资源组只允许拥有对应全局管理权限的角色。

## 14. 视频资源授权执行

### 14.1 统一接口

AuthorizationFacade 对业务代码暴露稳定接口：

~~~java
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

VideoScope resolveVideoScope(String permissionCode);

DepartmentScope resolveDepartmentScope(String permissionCode);
~~~

业务控制器和服务不读取角色表，也不自行判断超级管理员。

`ResourceDenialMode` 只有 FORBID 和 HIDE_AS_NOT_FOUND。详情、下载和可枚举对象默认使用 HIDE_AS_NOT_FOUND；PTZ、停止、对讲等已经明确目标的控制命令使用 FORBID。三参数重载默认 FORBID，调用方不得通过捕获异常把拒绝改成成功。

### 14.2 列表和统计

以下查询必须在数据库层过滤，不允许先查全量再在 Java 或前端删除：

- 设备、终端和通道列表；
- 通道树、分屏监控树；
- 地图点位和轨迹；
- 告警列表；
- 设备录像和云端录像；
- 拉流代理和推流列表；
- 截图、录像文件；
- 导出数据；
- 分页总数、首页统计卡片。

VideoScope 包含 all 标志、来源资源集合和显式通道集合。数据量较小时可使用绑定参数 IN；资源量较大时通过 wvp_role_resource_group、closure 和 resource_group_item 子查询 JOIN。不得把数千个 ID 长期塞进 JWT。

### 14.3 按 ID 操作

所有接收以下标识的接口都要做对象级授权：

- deviceId；
- channelId 或 gbId；
- JT terminal/channel ID；
- proxyId、pushId；
- recordId；
- alarmId；
- app + stream；
- mediaServerId；
- 文件路径和文件名；
- 批量 ID 集合。

批量操作必须验证全部 ID 均有权限；混入一个无权 ID 时整个操作失败。

OWASP 将“知道对象 ID 即可读取或修改”归类为 IDOR。复杂或随机 ID 只能降低猜测概率，不能替代每次对象级授权。

### 14.4 后台和协议任务

设备注册、SIP 心跳、目录同步、ZLM Hook、定时清理和 Redis RPC 不使用人工用户的数据范围。这些入口使用 InternalPrincipal，并通过调用来源、消息签名、serverId 和节点密钥认证。

内部身份只能调用明确标记的内部用例，不能被普通 HTTP 请求构造。

## 15. 媒体访问安全

### 15.1 当前绕过

只保护 WVP API 不足以形成视频权限，因为用户可以复制 ZLM 的 FLV、HLS、WebRTC、RTSP 或 RTMP 地址。现有分享页也直接接收完整媒体 URL。

### 15.2 播放票据

新增受保护接口：

~~~text
POST /api/media/play-ticket
~~~

请求包含通道或资源 ID、目标播放协议。服务端完成：

1. 检查对应 play 权限；
2. 检查目标资源；
3. 确认 app、stream 和 mediaServerId 的真实绑定；
4. 生成随机 jti；
5. 把票据上下文写入 Redis；
6. 返回带 ticket 的媒体 URL。

票据内容：

~~~text
jti
principalType
principalId
permissionCode
resourceType
resourceId
mediaServerId
app
stream
allowedSchemas
authzVersion
issuedAt
expiresAt
~~~

默认票据用于建立连接，有效期 120 秒。票据在有效期内可以为同一 app、stream 和允许协议重复使用，以兼容播放器建立多个连接；不能用于其他流。需要重新连接时，前端通过受保护接口申请新票据。

权限版本变化后，旧票据校验失败。网页登录 access-token 不放进媒体 URL。

### 15.3 on_play

ZLMediaKit 和 ABL 的 on_play 回调执行：

1. 校验媒体节点身份；
2. 读取 ticket；
3. 校验 app、stream、mediaServerId 和 schema；
4. 校验主体状态及 authzVersion；
5. 返回允许或拒绝。

rtp 应用、转码流、MP4 加载流和没有 callId 的流不能继续默认放行。内部 RTP 流如果不对外播放，应通过 internal 标识和网络隔离处理。

### 15.4 Hook 认证

/index/hook/** 保持不走用户 RBAC，但必须走 MediaHookAuthenticationFilter：

- 根据 mediaServerId 查找已登记节点；
- 校验每节点 Hook secret 或 admin_params；
- 可选来源 IP 白名单；
- 生产环境建议通过内网、反向代理或 mTLS；
- 重放敏感 Hook 时校验时间戳和 nonce；
- 伪造 Hook 返回 401，不发布应用事件。

### 15.5 分享

旧格式：

~~~text
/#/play/share?url=<完整媒体地址>
~~~

改为：

~~~text
/#/play/share/:shareId
~~~

只有拥有 video:stream:share 且拥有目标资源的用户可以创建分享。分享记录配置有效期、最大访问次数、是否需要提取码和撤销状态。分享页通过 shareId 换取专用播放票据，不能获得原始长期地址。

### 15.6 快照、录像和下载

- 设备快照、报警快照、云端录像和 ZIP 下载不再匿名；
- 登录用户通过功能权限和资源权限获取短期下载票据；
- 分享下载必须使用单独的 Share 权限；
- 下载票据绑定文件、用户、有效期和最大使用次数；
- 服务端通过记录 ID 解析真实路径，客户端不能提交任意服务器文件路径；
- Nginx 只代理通过内部鉴权后的资源，不直接暴露录像目录。

## 16. API Key

API Key 作为机器身份，不再伪装成网页登录 JWT。

实施分两步：阶段 0 先完成 P0 安全修复，包括随机 secret、HMAC 哈希存储、明文仅显示一次、本人/他人所有权、绝对 expired_at 和独立认证链；阶段 6 再增加菜单 permission scope、视频资源组 scope、IP 白名单和 LEGACY 清理。阶段 0 完成后即使细粒度 scope 尚未上线，也不得继续保存或列表返回明文 Key。

### 16.1 创建

- 用户只能创建自己的 Key，管理员管理他人 Key 需要 system:apikey:manageOthers；
- 创建者选择功能 scope 和视频资源组 scope；
- 选择范围必须是用户当前有效范围的子集；
- 服务端生成高熵随机值；
- 明文只在创建或轮换成功响应中出现一次；
- 数据库存哈希、前缀和末四位；
- 列表只返回掩码。

### 16.2 校验

有效权限：

~~~text
API Key 有效功能权限
    = 用户当前功能权限 ∩ API Key 功能 scope

API Key 有效视频资源
    = 用户当前视频资源 ∩ API Key 资源组 scope
~~~

每次校验检查用户状态、Key 状态、expired_at、IP 白名单和 authzVersion。删除用户、停用角色或收回资源后，Key 立即收缩或失效。

### 16.3 当前数据迁移

旧 API Key 不满足只存哈希和 scope 要求。升级时：

- 旧 Key 默认标记 LEGACY；
- 仅保留短兼容窗口；
- 管理员必须轮换为新 Key；
- 兼容窗口结束后拒绝 LEGACY；
- 不尝试在管理页面继续展示旧 Key 明文。

## 17. 后端接口

### 17.1 当前用户

~~~json
GET /api/auth/profile
{
  "user": {
    "id": 12,
    "username": "operator-a",
    "nickname": "一号楼值班员",
    "deptId": 8,
    "deptName": "监控中心"
  },
  "roles": ["operator"],
  "permissions": [
    "video:channel:list",
    "video:channel:play",
    "video:ptz:control"
  ],
  "authzVersion": 37
}
~~~

响应不返回密码、API Key、JWT 私有信息和全部资源 ID。

### 17.2 动态路由

~~~json
GET /api/menu/routers
[
  {
    "name": "Video",
    "path": "/video",
    "component": "Layout",
    "hidden": false,
    "meta": {
      "title": "视频管理",
      "icon": "devices"
    },
    "children": []
  }
]
~~~

后端只返回允许的菜单标识和受控组件名。前端使用组件白名单映射，不能把服务端字符串直接作为任意 import 路径。

### 17.3 管理 API

新增或重构：

- /api/system/user；
- /api/system/role；
- /api/system/dept；
- /api/system/menu；
- /api/system/resource-group；
- /api/system/api-key；
- /api/auth/profile；
- /api/menu/routers；
- /api/media/play-ticket；
- /api/media/download-ticket；
- /api/media/share。

旧 /api/user、/api/role 和 /api/userApiKey 保留一个版本的兼容适配器，内部调用新服务并返回弃用 Header，下一大版本移除。

## 18. 前端设计

保留 Vue 2、Vuex、Vue Router 和 Element UI。

### 18.1 路由

- constantRoutes 只保留登录、403、404、分享页和必要公共页面；
- 首次登录后请求 profile 和 routers；
- Vuex permission 模块生成 addRoutes；
- Sidebar 从 permission_routes 读取；
- 404 路由最后动态追加；
- 登出、token 失效和权限版本变化时清理动态路由、TagsView 和缓存页面；
- 未授权路由进入 403，不把用户强制退出。

### 18.2 状态

user 模块增加：

- id；
- name；
- dept；
- roles；
- permissions；
- authzVersion。

permission 模块维护：

- routes；
- addRoutes；
- generateRoutes；
- resetRoutes。

前端不保存完整视频资源 ID 权限集合。列表和操作结果以服务端授权为准。

### 18.3 按钮

提供：

~~~vue
<el-button v-hasPermi="['video:device:add']">添加设备</el-button>
<el-button v-hasPermi="['video:channel:play']">播放</el-button>
<el-button v-hasPermi="['video:ptz:control']">云台控制</el-button>
~~~

同时提供 this.$auth.hasPermi()，用于右键菜单、播放器工具栏、动态列和下拉菜单。

### 18.4 管理页面

#### 用户

左侧部门树，右侧用户列表。支持多角色、状态、重置密码和 API Key 管理。普通部门管理员只能操作其部门范围内用户。

#### 角色

包含基本信息、菜单/按钮树、部门数据范围、视频范围类型和视频资源组树。保存时在一个事务中更新角色、菜单、部门和资源组关系。

#### 部门

独立人员组织树，不能复用行政区划或 GB 业务分组。

#### 菜单

支持目录、菜单、按钮三类节点，按钮节点维护 permissionCode。

#### 视频资源组

左侧资源组树，右侧资源项列表。支持添加整台设备、终端、推流、拉流或单个通道；界面明确显示“整台设备会自动包含以后同步的新通道”。

#### API Key

列表只显示名称、前缀、末四位、scope、状态、过期时间和最近使用时间。明文弹窗只在创建和轮换成功时显示一次。

## 19. 公开端点和内部端点

### 19.1 用户公开端点

原则上只保留：

- POST /api/auth/login；
- 通过 shareId 访问且受分享规则约束的接口；
- 健康检查中不泄露内部信息的 liveness。

Swagger 生产默认关闭。

### 19.2 媒体和内部端点

- /index/hook/zlm/**：媒体节点认证；
- /index/hook/abl/**：媒体节点认证；
- Redis RPC：消息签名和目标 serverId；
- SIP、JT1078 协议入口：协议自身认证和内部服务身份；
- readiness：仅内网或运维权限。

快照、录像、日志 WebSocket、JT 下载和 record_proxy 不再因为路径方便而匿名放行。

## 20. 审计

以下操作必须记录成功和拒绝结果：

- 登录、登出、锁定、解锁；
- 用户、角色、部门和菜单变更；
- 视频资源组和角色资源范围变更；
- API Key 创建、轮换、停用和删除；
- 点播、PTZ、对讲、录像下载、录像删除；
- 分享创建、访问和撤销；
- 直接 ID 越权；
- 媒体票据校验失败；
- Hook 身份校验失败。

审计记录包含 requestId，业务日志和媒体 Hook 日志可以用该 ID 关联。高频播放成功日志可采样或汇总，拒绝日志不采样。

## 21. 数据库迁移

建议作为 2.8.0 大版本建立：

- 数据库/2.8.0/初始化-mysql-2.8.0.sql；
- 数据库/2.8.0/更新-mysql-2.8.0.sql；
- 数据库/2.8.0/初始化-postgresql-2.8.0.sql；
- 数据库/2.8.0/更新-postgresql-2.8.0.sql；
- 数据库/2.8.0/初始化-kingbase-2.8.0.sql；
- 数据库/2.8.0/更新-kingbase-2.8.0.sql；
- 数据库/2.8.0-h2/h2-schema.sql；
- 数据库/2.8.0-h2/h2-data.sql；
- 同步 docker/mysql/db/wvp.sql。

### 21.1 升级顺序

1. 发布前备份数据库并记录 2.7.4 应用镜像、配置和校验值；
2. expand：保留旧 wvp_user_role 角色主表，新建 wvp_role 和临时关系表 wvp_user_role_rel；
3. 创建 root 部门、菜单、闭包、资源组和其他关系表；
4. 扩展用户、角色、API Key、授权审计和云端录像字段；
5. 将旧角色复制到 wvp_role，把每个旧 wvp_user.role_id 回填到 wvp_user_role_rel；
6. 将原管理员角色标记为不可删除的 super_admin system_role，不依赖固定 ID；
7. 创建默认资源组“全部资源”，对超级管理员配置 ALL 视频范围；
8. 兼容后端双写旧 role_id 和 wvp_user_role_rel，读授权只使用新表；
9. 其他旧角色进入 LEGACY_COMPAT：升级脚本把 2.7.4 已有的视频、运维、用户查询和本人 API Key 能力显式写入 `wvp_role_menu`，不授予角色/部门/菜单管理或他人 Key；仅 `update_by is null` 的迁移角色允许播种，管理员第一次保存角色会整体替换关系并永久退出兼容态；
10. 运行关系数量、孤儿数据、闭包和授权差异核对；
11. enforce 稳定且明确不再回滚旧应用后进入停机 contract；
12. contract：删除旧 wvp_user_role 角色主表和旧 authority，将 wvp_user_role_rel 重命名为最终 wvp_user_role，并校正 sequence/自增值；
13. 下一大版本删除 wvp_user.role_id 和 LEGACY 兼容代码。

### 21.2 兼容与灰度

增加配置：

~~~yaml
authorization:
  mode: audit
~~~

模式：

- disabled：仅开发回退，不允许生产；
- audit：计算权限、记录拒绝，但暂不阻断；
- enforce：正式阻断。

方法权限统一使用模式感知 Bean，例如 `@PreAuthorize("@authz.check('video:channel:play')")`。`PermissionEnforcer.check` 在 audit 记录真实 DENY 后返回 true，在 enforce 返回真实判定；角色、用户、菜单、部门、API Key、资源组等授权配置接口必须使用 `@authz.require(...)`，在 disabled、audit、enforce 三种模式下都返回真实判定。直接使用 `hasAuthority` 会绕过 audit 语义，禁止用于受模式控制的业务端点。

生产发布顺序为备份 → expand schema → 兼容双写后端 → 权限配置 → audit 观察 → enforce → contract。contract 前可以回滚旧应用和 expand SQL；contract 后只能通过数据库快照恢复。旧角色不得在 enforce 阶段默认拥有全部资源。

### 21.3 多数据库

- 部门和资源组树使用 closure 表，不使用 find_in_set；
- MySQL 主键使用 BIGINT AUTO_INCREMENT；
- PostgreSQL 和 Kingbase 使用各自脚本定义 BIGSERIAL 或显式 sequence；
- 显式写入 ID=1 后校正自增值或 sequence；
- PostgreSQL 和 Kingbase 的注释分别使用各自验证通过的 COMMENT ON 语法；
- 标识符使用小写并避开 key、user、order 等保留字；
- 状态字段使用 smallint；
- 时间字段在新模块统一用 timestamp，Java 使用 LocalDateTime；
- 关系表外键可以 CASCADE；
- 业务资源关联删除采用应用服务清理和孤儿扫描；
- PostgreSQL 与 Kingbase 不共用初始化/更新文件；两套脚本分别执行和验证，不假设 Kingbase 完全兼容 PostgreSQL 方言；
- H2 schema 和 data 与生产结构同步。

## 22. 分阶段实施

### 阶段 0：安全基线与权限清单

产出反射发现全部 HTTP Handler 的权限矩阵、公开端点清单、登录/401/403 基线测试。修复实体敏感字段泄漏和 API Key P0 问题；矩阵和测试不得硬编码控制器或端点数量。

### 阶段 1：身份认证硬化

完成 POST 登录、旧密码升级、用户状态、JWT 精简、tokenVersion、STATELESS 评估、401/403 处理和生产认证开关约束。

### 阶段 2：功能 RBAC

完成多角色、菜单/按钮、动态路由、GrantedAuthority 和方法权限底座。依赖部门范围的用户、角色、部门、菜单管理 API 与页面随阶段 3 一起交付，避免先提供无法正确过滤数据的管理入口。

### 阶段 3：部门数据范围

完成部门树、闭包表、角色部门范围，提供若依式用户、角色、部门、菜单管理 API 与页面，并把部门范围用于用户、API Key、部门和操作日志的数据过滤。

### 阶段 4：视频资源组

完成资源组树、来源资源和通道资源绑定、角色资源范围、列表过滤及新增通道继承。

### 阶段 5：对象级授权

覆盖点播、停止、PTZ、对讲、录像、下载、告警、截图、推流、拉流、JT1078 和兼容 API，封堵 IDOR。

### 阶段 6：媒体和机器凭证

完成播放票据、分享、下载票据、ZLM/ABL Hook 认证、API Key permission/resource scope、LEGACY Key 清理和日志 WebSocket 认证。API Key 的哈希存储、一次显示和所有权边界已经在阶段 0 完成。

### 阶段 7：多数据库、灰度和清理

完成四类数据库验证、audit 模式观察、enforce、旧 role_id 和 LEGACY API Key 清理。

阶段 0 到阶段 7 每个阶段均应独立可回滚，不能把全部改造压在一次发布中。

### 22.1 实施计划拆分

本设计覆盖多个相互依赖的子系统，不适合生成一份超长实施计划后一次执行。设计评审通过后，拆成四份可独立验收的实施计划：

1. 身份认证硬化与功能 RBAC：阶段 0～2，包含 API Key P0；
2. 部门数据范围与系统管理前端：阶段 3；
3. 视频资源组、查询过滤和对象级授权：阶段 4～5；
4. 媒体票据、分享、API Key scope、多数据库灰度：阶段 6～7。

后一个计划可以依赖前一个计划公开的接口，但不得绕过其服务直接访问角色、部门或资源关系表。

已形成可执行文档：

- `docs/superpowers/plans/2026-07-13-wvp-rbac-implementation-index.md`；
- `docs/superpowers/plans/2026-07-13-wvp-authentication-functional-rbac.md`，12 个任务；
- `docs/superpowers/plans/2026-07-13-wvp-department-data-scope.md`，9 个任务；
- `docs/superpowers/plans/2026-07-13-wvp-video-resource-authorization.md`，12 个任务；
- `docs/superpowers/plans/2026-07-13-wvp-media-access-api-key-rollout.md`，11 个任务。

总计 44 个可独立评审、测试和提交的任务。实施顺序、跨计划接口和发布批次以总索引为准。

### 22.2 阶段 0～2 的实现基线

计划 1 交付的是后续数据范围和视频资源授权可复用的认证、功能权限与前端路由底座，不代表第 26 章的全项目最终验收已经完成。当前基线如下：

| 范围 | 已落地能力 | 明确边界 |
|---|---|---|
| 阶段 0 | 反射发现并分类 402 个 HTTP Handler；机器清单包含 `PUBLIC 2`、`AUTHENTICATED 4`、`REQUIRE_PERMISSION 36`、`REQUIRE_RESOURCE 338`、`MEDIA_HOOK 22`；API Key 使用哈希存储、明文只返回一次并校验所有权 | `REQUIRE_RESOURCE` 只执行功能权限 decision，尚未伪造对象级授权；`MEDIA_HOOK` 进入独立的 `DEFERRED_MEDIA_AUTH` 兼容链，不进入普通用户匿名白名单，节点认证由计划 4 替换启用 |
| 阶段 1 | POST JSON 登录、旧 MD5 密码首次登录升级 BCrypt、无密码主体、精简 JWT、`tokenVersion` 失效、统一 401/403、退出撤销和生产配置约束 | 旧版 MD5 登录仅作为带开关和 Sunset 的 `DEPRECATED` 兼容入口；浏览器不再提交 MD5 |
| 阶段 2 | 用户多角色、角色菜单、系统角色权限展开、真实 `GrantedAuthority`、`PermissionEnforcer` 的 audit/enforce 语义、`profile`/`routers` 协议、前端动态路由与按钮权限 | 若依式用户/角色/部门/菜单完整管理页在计划 2 接入部门范围后启用；视频资源组和对象授权在计划 3 实现 |

后续计划只能依赖以下公开契约，不得绕过服务直接拼接角色关系：

- `AuthPrincipal`：统一人工用户和后续机器主体的无凭证身份，不包含密码、pushKey 或 JWT 原始 claims；
- `PermissionService`：按启用角色合并功能权限，系统角色按启用权限节点展开；
- `TokenVersionService`：用户状态、密码和授权关系变化后的旧令牌失效边界；
- `GET /api/auth/profile` 与 `GET /api/menu/routers`：前端权限状态和动态路由的唯一初始化协议；
- `authorization.mode`：`audit` 记录真实 ALLOW/DENY 但兼容放行，`enforce` 才执行真实阻断。

端点清单的机器事实源是 `src/test/resources/security/controller-permission-matrix.csv`，可读镜像是 `docs/security/endpoint-permission-matrix.md`。反射测试必须双向验证“代码中没有漏项、CSV 中没有失效项”，不得硬编码控制器名单或端点总数。

本阶段数据库菜单只启用当前前端确实存在且已加入固定组件白名单的页面。角色、部门、菜单、API Key 和视频资源组等后续管理页面在对应页面与数据权限实现前保持禁用；未知组件字符串必须拒绝，不能由服务端字符串动态拼接 `import()`。

浏览器端不再读取长期 `pushKey`。GB28181/JT1078 内部对讲流按媒体服务既有规则不使用普通推流 `pushKey`；原“构建推流地址”页面依赖可长期复用的浏览器 MD5 签名，已显式停用。ZLM/ABL Hook 已从历史的 `DEFERRED_MEDIA_AUTH` 兼容链迁移为 `ACTIVE` 逐节点认证链，使用独立密钥哈希、类型、CIDR 与重放防护，不接受用户 JWT/API Key。

阶段验收以计划 1 Task 12 的后端集成测试、端点反射测试、完整 Jest、ESLint 和静态安全扫描为准。只有测试进程实际执行且 Surefire 汇总 `Tests run` 大于 0 才算后端通过；文档和单独 SQL 解析成功不能替代该门禁。

## 23. 测试

### 23.1 后端

需要新增 spring-security-test。条件允许时用 Testcontainers 验证 MySQL 和 PostgreSQL；Kingbase 使用独立 CI 数据库执行相同 migration 和 DAO contract suite。

必测：

- 匿名请求返回 401；
- 已登录无权限返回 403；
- 用户停用、锁定、删除后旧 token 失效；
- 修改角色、菜单和资源组后 tokenVersion 生效；
- 多角色功能权限并集；
- 权限与资源范围不跨角色串用；
- 部门 ALL、CUSTOM、DEPT、DEPT_AND_CHILD、SELF、NONE；
- 资源组父子继承和多组并集；
- 整台设备授权自动包含新通道；
- 显式通道授权不放大为整台设备；
- 列表、分页、搜索、统计和导出只返回授权数据；
- 手工替换 deviceId、channelId、recordId、alarmId、proxyId 不能越权；
- 批量请求混入无权 ID 时整体拒绝；
- 后台 SIP、Hook 和定时任务不受人工用户范围误伤；
- 超级管理员不可被普通管理员停用或降权。

### 23.2 媒体

- 无 ticket 直接访问旧媒体地址失败；
- ticket 过期、篡改、替换 app/stream/mediaServerId/schema 失败；
- 权限收回后旧 ticket 不能建立新连接；
- FLV、WS-FLV、HLS、WebRTC、RTSP、RTMP 分别验证；
- 转码流和录像加载流不能逃逸原资源；
- 伪造 ZLM/ABL Hook 失败；
- 分享过期、撤销、次数耗尽和提取码错误失败；
- 快照和录像下载票据不能替换文件；
- 多媒体节点使用同一授权规则。

### 23.3 前端

- 登录后只注册允许的路由；
- 刷新页面能恢复动态路由；
- 未授权 URL 进入 403；
- 父菜单无可见子项时不展示；
- 登出后动态路由和 TagsView 清理；
- 401 与 403 行为不同；
- 按钮隐藏后手工调用 API 仍被后端拒绝；
- 播放器、右键菜单、下拉操作和动态工具栏没有漏掉权限；
- API Key 明文只显示一次；
- 资源组树的大数据量选择保持可用。

### 23.4 数据库

- 全新初始化可用；
- 2.7.4 升级到 2.8.0 后旧管理员可登录；
- 旧用户角色关系回填准确；
- 部门和资源组 closure 无断链；
- 自增 ID/sequence 正确；
- MySQL、PostgreSQL、Kingbase 查询结果一致；
- 回滚到 audit 模式不丢权限配置。

## 24. 性能

- 权限和资源组结构缓存到 Redis；
- 缓存 key 包含版本号，不做大范围模糊删除；
- 用户 JWT 不携带资源 ID 集合；
- 大资源集合通过 JOIN 过滤，不生成超长 IN；
- 资源组 closure 和关系表建立双向索引；
- 首页统计和树查询复用授权子查询；
- 点播前的对象级授权应在数据库和缓存之间保持一次稳定查询，不重复扫描全部角色；
- on_play 使用 Redis 读取短期票据，不能每次查询完整角色菜单树；
- 缓存或 Redis 故障时，敏感操作拒绝，不能默认允许。

## 25. 风险与控制

| 风险 | 控制措施 |
|---|---|
| 439 个端点漏标权限 | 端点分类清单和启动/测试扫描 |
| 列表过滤了但按 ID 可越权 | ResourceAuthorizationService 统一对象检查 |
| 不同角色权限和资源串用 | 按 permissionCode 选择有效角色 |
| 复制 ZLM URL 绕过 WVP | 播放票据、on_play 校验、网络入口收敛 |
| 权限变更后缓存过期 | tokenVersion 和版本化 Redis key |
| 旧用户发布后无法操作 | audit 模式、兼容角色、灰度切换 |
| 部门和视频资源混淆 | 两套独立树和独立关系表 |
| 设备新通道没有授权 | 来源资源授权动态包含子通道 |
| 旧录像无法找到通道 | LEGACY_UNBOUND，只允许超级管理员 |
| 通用资源关系没有物理 FK | 写入校验、删除清理、孤儿扫描 |
| API Key 扩大用户权限 | 权限和资源取交集 |
| 多数据库方言不一致 | 独立脚本和 DAO contract suite |
| 媒体 Hook 被伪造 | 每节点密钥、来源验证、可选 mTLS |

## 26. 验收标准

全部满足后才可以把 authorization.mode 切换到 enforce：

1. 每个外部端点都在权限矩阵中；
2. 前端没有仅凭静态 constantRoutes 暴露全部页面；
3. 后端 Authentication 包含真实权限；
4. 用户可以绑定多个角色；
5. 部门范围在用户管理中生效；
6. 角色可以绑定视频资源组；
7. 功能权限与资源范围按同一角色计算；
8. 设备级授权自动覆盖以后同步的新通道；
9. 设备、通道、录像、告警和流代理列表均过滤；
10. 点播、PTZ、下载等直接 ID 操作无法越权；
11. ZLM/ABL 播放必须通过短期票据；
12. 分享不暴露长期原始媒体 URL；
13. API Key 只存哈希、只显示一次、不能扩大用户权限；
14. 权限变化可以使旧 token 和新连接票据失效；
15. 401、403、404 语义统一；
16. MySQL、PostgreSQL、Kingbase 和 H2 数据模型通过验证；
17. 现有 GB28181 点播、回放、PTZ、对讲、拉流代理和推流回归通过；
18. audit 模式至少覆盖一个完整业务周期且无未分类入口。

## 27. 工作量估算

以下估算以一名熟悉 Spring Security、MyBatis 和 Vue 2 的开发者为基准，不包含需求反复和生产数据清洗：

| 阶段 | 人日 |
|---|---:|
| 安全基线、权限矩阵 | 4～7 |
| 身份认证硬化 | 5～8 |
| 功能 RBAC 和动态菜单 | 8～12 |
| 部门数据范围 | 5～8 |
| 视频资源组 | 8～12 |
| 对象级业务接口授权 | 10～18 |
| 媒体票据、分享、Hook、API Key | 8～14 |
| 多数据库、灰度、回归 | 8～12 |
| 合计 | 56～91 |

两名开发者可以并行后端授权核心与前端管理页面，但数据库模型、权限编码和媒体票据必须由同一设计负责人把关。完整改造属于中大型功能，不建议用一次合并完成。

## 28. 主要文件影响范围

### 后端基础

- src/main/java/com/genersoft/iot/vmp/conf/security/；
- src/main/java/com/genersoft/iot/vmp/vmanager/user/；
- src/main/java/com/genersoft/iot/vmp/storager/dao/UserMapper.java；
- src/main/java/com/genersoft/iot/vmp/storager/dao/RoleMapper.java；
- src/main/java/com/genersoft/iot/vmp/storager/dao/UserApiKeyMapper.java；
- src/main/java/com/genersoft/iot/vmp/service/impl/UserServiceImpl.java；
- src/main/java/com/genersoft/iot/vmp/service/impl/RoleServerImpl.java；
- src/main/java/com/genersoft/iot/vmp/conf/GlobalExceptionHandler.java。

### 视频业务

- src/main/java/com/genersoft/iot/vmp/gb28181/controller/；
- src/main/java/com/genersoft/iot/vmp/jt1078/controller/；
- src/main/java/com/genersoft/iot/vmp/streamProxy/controller/StreamProxyController.java；
- src/main/java/com/genersoft/iot/vmp/streamPush/controller/StreamPushController.java；
- src/main/java/com/genersoft/iot/vmp/vmanager/alarm/AlarmController.java；
- src/main/java/com/genersoft/iot/vmp/vmanager/cloudRecord/CloudRecordController.java；
- src/main/java/com/genersoft/iot/vmp/vmanager/recordPlan/RecordPlanController.java；
- src/main/java/com/genersoft/iot/vmp/vmanager/rtp/RtpController.java；
- src/main/java/com/genersoft/iot/vmp/vmanager/ps/PsController.java；
- src/main/java/com/genersoft/iot/vmp/web/gb28181/；
- src/main/java/com/genersoft/iot/vmp/web/custom/CameraChannelController.java。

### 媒体安全

- src/main/java/com/genersoft/iot/vmp/service/impl/MediaServiceImpl.java；
- src/main/java/com/genersoft/iot/vmp/media/zlm/ZLMHttpHookListener.java；
- src/main/java/com/genersoft/iot/vmp/media/abl/ABLHttpHookListener.java；
- src/main/java/com/genersoft/iot/vmp/gb28181/controller/MediaController.java；
- src/main/java/com/genersoft/iot/vmp/conf/webLog/LogChannel.java。

### 前端

- web/src/router/index.js；
- web/src/permission.js；
- web/src/store/modules/user.js；
- web/src/store/getters.js；
- web/src/layout/components/Sidebar/；
- web/src/utils/request.js；
- web/src/views/user/；
- 各业务页面的操作按钮和播放器组件。

实现计划阶段会把以上范围拆成精确的新增、修改和测试文件，并按可独立验收的任务排序。

## 29. 参考

- Spring Security 方法授权：<https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html>
- 若依后台与数据权限：<https://doc.ruoyi.vip/ruoyi-vue/document/htsc.html>
- OWASP Authorization Cheat Sheet：<https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html>
- OWASP IDOR Prevention Cheat Sheet：<https://cheatsheetseries.owasp.org/cheatsheets/Insecure_Direct_Object_Reference_Prevention_Cheat_Sheet.html>
- ZLMediaKit WebHook：<https://docs.zlmediakit.com/guide/media_server/web_hook_api.html>
- ZLMediaKit REST API：<https://docs.zlmediakit.com/guide/media_server/restful_api.html>

## 30. 评审结论

本设计采用“若依式系统权限 + 独立视频资源组”的组合。部门只管理人员组织数据，视频资源通过角色绑定资源组；两者在授权服务中并列计算，不互相冒充。

设计评审通过后，再生成逐文件、逐测试、逐提交的实施计划。实施计划不得在未完成权限矩阵、迁移脚本和安全基线测试时直接批量给控制器加注解。
