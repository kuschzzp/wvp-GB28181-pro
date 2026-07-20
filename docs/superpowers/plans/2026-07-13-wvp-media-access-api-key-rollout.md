# WVP Media Access, Scoped API Keys, and Rollout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

> **实施状态（2026-07-15）：实施中。** Task 1 已完成 API Key permission/resource scope、CIDR 白名单、独立机器认证和管理端配置；Task 2 已完成活动流绑定、120 秒资源绑定播放票据及签发接口；Task 3 已完成长期媒体地址收口、活动流安全引用、票据 URL 工厂和受 Sunset 约束的 audit 兼容模式；Task 4 已完成 ZLM/ABL 逐节点 Hook 密钥、CIDR、重放防护、独立过滤链、轮换与 Docker 网络收口，28 项定向测试和 Compose 静态校验通过；Task 5 已完成 ZLM/ABL `on_play` 票据强校验、ABL 显式协议映射和 fail-open 清理，16 项定向测试通过；Task 6 已完成所有播放器统一按稳定资源引用申请、续领短票据，并拆分音频对讲发布端点与回传播放票据，后端 15 项、前端 12 项定向测试通过；Task 7 已完成受控分享、口令、次数、撤销和公开短票据兑换，后端 23 项、前端 19 项定向测试通过。当前实施 Task 8。本文中的提交命令仅作为原始变更边界参考，按用户要求不执行。

**Goal:** 用短期播放/下载票据、媒体节点身份、受控分享和受限 API Key 封闭绕过 WVP 的媒体访问路径，并完成多数据库验证、audit 到 enforce 灰度和最终 contract 迁移。

**Architecture:** 业务点播只返回资源引用和活动流绑定，不返回长期媒体地址；`PlayTicketService` 在资源授权后签发 120 秒 Redis 票据，ZLM/ABL `on_play` 同时验证节点身份和票据。API Key 是独立机器主体，其权限和资源范围分别与所属用户当前范围取交集。部署采用 expand → audit → 双轨票据 → enforce → contract。

**Tech Stack:** Java 21、Spring Boot 3.4.4、Spring Security 6、Redis、MyBatis、ZLMediaKit/ABL Hook、JUnit 5、Testcontainers（MySQL/PostgreSQL）、Vue 2、Jest、Nginx。

## Global Constraints

- 必须先完成计划 1～3，复用 `AuthPrincipal`、`AuthorizationFacade`、`ResourceResolverRegistry` 和 `TokenVersionService`。
- 计划 1 已完成 API Key P0：哈希存储、一次显示、所有权和绝对过期时间；本计划只增加 scope、独立机器认证和 LEGACY 清理。
- 浏览器 access-token 不得写入媒体 URL；媒体 URL 只允许携带短期 opaque ticket。
- 播放票据默认 120 秒，只绑定一个 mediaServerId/app/stream/resource/authzVersion 和允许 schema 集合。
- ZLM/ABL Hook 不走用户 JWT，但必须先验证已登记媒体节点身份；未知节点和未知 schema fail closed。
- rtp、转码流、MP4 加载流、缺 callId/缺 ticket 的外部播放不得默认允许。
- 分享页使用 shareId，不接受 `?url=`；下载接口使用记录 ID 和短票据，不接受任意服务器路径。
- API Key 有效权限 = 用户当前权限 ∩ Key permission scope；有效资源 = 用户当前视频范围 ∩ Key 资源组 scope。
- audit 阶段记录本应拒绝的播放但允许兼容双轨；enforce 后旧原始 URL、LEGACY Key 和匿名下载全部拒绝。
- PostgreSQL 与 Kingbase 使用独立脚本和独立验证；contract 后回退必须恢复数据库快照。

---

## 1. 文件结构与责任

### 媒体安全核心

| 路径 | 责任 |
|---|---|
| `src/main/java/com/genersoft/iot/vmp/media/security/MediaStreamBinding.java` | 节点、app、stream 与授权资源绑定 |
| `src/main/java/com/genersoft/iot/vmp/media/security/MediaSchema.java` | FLV、WS_FLV、HLS、WEBRTC、RTSP、RTMP |
| `src/main/java/com/genersoft/iot/vmp/media/security/MediaStreamBindingResolver.java` | 从资源引用解析活动流 |
| `src/main/java/com/genersoft/iot/vmp/media/security/PlayTicketService.java` | 签发和验证播放票据 |
| `src/main/java/com/genersoft/iot/vmp/media/security/TicketedMediaUrlFactory.java` | 给 ZLM/ABL URL 安全追加 ticket |
| `src/main/java/com/genersoft/iot/vmp/media/security/MediaHookAuthenticationService.java` | 每节点 Hook secret 校验 |
| `src/main/java/com/genersoft/iot/vmp/media/security/MediaHookAuthenticationFilter.java` | Hook 独立过滤链 |
| `src/main/java/com/genersoft/iot/vmp/media/security/AblSchemaResolver.java` | networkType 到 schema 的显式配置映射 |
| `src/main/java/com/genersoft/iot/vmp/media/security/ShareService.java` | 创建、交换、撤销分享 |
| `src/main/java/com/genersoft/iot/vmp/media/security/DownloadTicketService.java` | 记录 ID 到受控文件票据 |
| `src/main/java/com/genersoft/iot/vmp/vmanager/media/MediaAccessController.java` | play-ticket/download-ticket |
| `src/main/java/com/genersoft/iot/vmp/vmanager/media/StreamShareController.java` | 分享管理和公开交换 |

### API Key

| 路径 | 责任 |
|---|---|
| `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationService.java` | 扩展计划 1 的 Key 状态、IP、版本和 scope |
| `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationFilter.java` | 扩展计划 1 的独立 `api-key` 过滤器 |
| `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyScopeService.java` | 用户与 Key 范围取交集 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/ApiKeyPermissionMapper.java` | Key 菜单权限关系 |
| `src/main/java/com/genersoft/iot/vmp/storager/dao/ApiKeyResourceGroupMapper.java` | Key 资源组关系 |

### 前端

| 路径 | 责任 |
|---|---|
| `web/src/api/mediaAccess.js` | 播放/下载票据和分享交换 |
| `web/src/utils/mediaSource.js` | 协议映射、重连和并发取消 |
| `web/src/views/common/playerTabs.vue` | 所有播放器统一票据入口 |
| `web/src/views/common/share.vue` | shareId 页面 |
| `web/src/views/user/apiKeyManager.vue` | 掩码、scope 和一次性 secret |

---

### Task 1: 完成 API Key permission/resource scope 和独立机器认证

**Files:**
- Modify: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0-h2/h2-schema.sql`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationFilter.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyScopeService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/ApiKeyPermissionMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/ApiKeyResourceGroupMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyAuthenticationServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/apikey/ApiKeyScopeServiceTest.java`

**Interfaces:**
- Produces: `AuthPrincipal authenticate(String rawKey, InetAddress clientAddress)`。
- Produces: `Set<String> resolvePermissions(long apiKeyId, AuthPrincipal owner)`。
- Produces: `VideoScope resolveVideoScope(long apiKeyId, String permissionCode, AuthPrincipal owner)`。

- [x] **Step 1: 写交集和状态失败测试**

覆盖用户有权限但 Key 未勾选、Key 勾选但用户已收回、资源组交集、过期、停用、撤销、用户停用、authzVersion 不一致、IP 不在 CIDR、Redis/数据库异常。

- [x] **Step 2: 创建关系表**

```sql
create table wvp_api_key_permission (
  api_key_id bigint not null,
  menu_id bigint not null,
  primary key (api_key_id, menu_id),
  foreign key (api_key_id) references wvp_user_api_key(id) on delete cascade,
  foreign key (menu_id) references wvp_menu(id) on delete cascade
);

create table wvp_api_key_resource_group (
  api_key_id bigint not null,
  resource_group_id bigint not null,
  primary key (api_key_id, resource_group_id),
  foreign key (api_key_id) references wvp_user_api_key(id) on delete cascade,
  foreign key (resource_group_id) references wvp_resource_group(id) on delete cascade
);
```

- [x] **Step 3: 独立认证链**

`ApiKeyAuthenticationFilter` 只读取 `api-key` Header；`JwtAuthenticationFilter` 只读取 `access-token`。两者都拒绝同时提供两种凭证，避免主体混淆。成功主体为 `PrincipalType.API_KEY`，id=apiKeyId、userId=ownerId、credentials=null。

- [x] **Step 4: 实现范围交集**

Key permission 只允许绑定 `wvp_menu.perms` 非空且启用的节点。创建/轮换/修改 scope 时验证它是用户当前范围子集；每次请求仍实时取交集，不能依赖创建时验证永久有效。

- [x] **Step 5: 运行测试（按用户要求不提交）**

Run: `mvn -Dtest=ApiKeyAuthenticationServiceTest,ApiKeyScopeServiceTest test`

Expected: PASS，任何一侧收回后 Key 立即收缩或失效。

```bash
git add 数据库/2.8.0 数据库/2.8.0-h2 src/main/java/com/genersoft/iot/vmp/auth/apikey src/main/java/com/genersoft/iot/vmp/storager/dao src/main/java/com/genersoft/iot/vmp/conf/security src/test/java/com/genersoft/iot/vmp/auth/apikey
git commit -m "feat(security): add scoped API key principals"
```

---

### Task 2: 建立活动流绑定和短期播放票据

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/MediaStreamBinding.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/MediaSchema.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/MediaStreamBindingResolver.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/impl/MediaStreamBindingResolverImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/PlayTicketService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/impl/PlayTicketServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/dto/PlayTicketRequest.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/dto/PlayTicketResponse.java`
- Create: `src/main/java/com/genersoft/iot/vmp/vmanager/media/MediaAccessController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/PlayTicketServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/PlayTicketControllerTest.java`

**Interfaces:**
- Produces: `MediaStreamBinding resolve(VideoResourceType type, long resourceId)`。
- Produces: `PlayTicketResponse issue(PlayTicketRequest request)`。
- Produces: `PlayTicketDecision verify(MediaPlayHookRequest request)`。

- [x] **Step 1: 写票据绑定失败测试**

覆盖过期、篡改、换 mediaServerId、换 app、换 stream、换 schema、换资源、用户停用、tokenVersion 改变、Redis key 缺失和同绑定 TTL 内复用。

- [x] **Step 2: 定义绑定和 Hook 请求**

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

- [x] **Step 3: 签发 opaque ticket**

使用 `SecureRandom` 生成 32 字节 base64url secret；Redis key 使用服务端 pepper 的 HMAC-SHA-256，而不是 raw ticket。值包含 principalType/principalId/userId/permissionCode/resource/binding/allowedSchemas/authzVersion/issuedAt/expiresAt，TTL 120 秒。

- [x] **Step 4: 先授权再签发**

请求只接收 resourceType/resourceId/schema。服务调用 `AuthorizationFacade.requireVideoResource`，再由 BindingResolver 决定节点/app/stream；客户端不能覆盖这些字段。allowedSchemas 使用枚举 `FLV,WS_FLV,HLS,WEBRTC,RTSP,RTMP`。

- [x] **Step 5: 运行测试（按用户要求不提交）**

Run: `mvn -Dtest=PlayTicketServiceTest,PlayTicketControllerTest test`

Expected: PASS；ticket 只对原始绑定和允许协议生效。

```bash
git add src/main/java/com/genersoft/iot/vmp/media/security src/main/java/com/genersoft/iot/vmp/vmanager/media src/test/java/com/genersoft/iot/vmp/media/security
git commit -m "feat(media): issue resource-bound play tickets"
```

---

### Task 3: 停止向前端泄露长期媒体地址

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/TicketedMediaUrlFactory.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/PlayableStreamService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/LegacyMediaResponsePolicy.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/MediaAccessProperties.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/dto/PlayableStreamRef.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/dto/LegacyMediaResponse.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/bean/StreamContent.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/service/impl/MediaServerServiceImpl.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/zlm/ZLMMediaNodeServerService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/abl/ABLMediaNodeServerService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlayController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/ChannelController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/MediaController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/PlaybackController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/cloudRecord/CloudRecordController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078Controller.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamProxy/controller/StreamProxyController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/streamPush/controller/StreamPushController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/web/custom/CameraChannelController.java`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/TicketedMediaUrlFactoryTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/MediaResponseLeakTest.java`

**Interfaces:**
- Produces: 点播响应 `PlayableStreamRef(resourceType,resourceId,mediaServerId,app,stream)`，无 URL。
- Produces: `Map<MediaSchema, URI> TicketedMediaUrlFactory.create(binding, rawTicket, schemas)`，只在 play-ticket 响应使用。

- [x] **Step 1: 写敏感响应扫描失败测试**

序列化点播、回放、JT、proxy、push 和云录像响应，断言不包含 `rtsp://`、`rtmp://`、`.flv`、`.m3u8`、`ws://`、`webrtc`、`callId=`、媒体节点 secret。

- [x] **Step 2: 修复当前 authority 参数失效**

删除或实现 `MediaServerServiceImpl.getStreamInfoByAppAndStreamWithCheck(String app, String stream, String mediaServerId, String addr, boolean authority)` 中未使用的 authority 参数。新路径不接受 boolean 安全开关：业务响应只返回 PlayableStreamRef，内部节点地址构造只能由 TicketedMediaUrlFactory 调用。

- [x] **Step 3: 安全追加 ticket**

使用 URI builder 保留既有 query 并 URL encode ticket；禁止字符串直接拼 `?ticket=`。ZLM 和 ABL 节点服务继续负责协议基础 URL，Factory 负责统一追加 ticket，避免两套实现分叉。

- [x] **Step 4: 兼容模式双轨**

audit 阶段可用受配置控制的 legacyResponse 字段给旧前端，但默认关闭、写弃用审计并设置明确 Sunset；enforce 模式无论配置如何都禁止返回 legacy URLs。

- [x] **Step 5: 运行测试（按用户要求不提交）**

Run: `mvn -Dtest=TicketedMediaUrlFactoryTest,MediaResponseLeakTest test`

Expected: enforce 响应无长期 URL，ticket URL 正确编码并绑定协议。

```bash
git add src/main/java/com/genersoft/iot/vmp/media src/main/java/com/genersoft/iot/vmp/vmanager/bean/StreamContent.java src/main/java/com/genersoft/iot/vmp/gb28181/controller src/test/java/com/genersoft/iot/vmp/media/security
git commit -m "feat(media): stop exposing persistent media URLs"
```

---

### Task 4: 为 ZLM/ABL Hook 增加每节点身份认证

**Files:**
- Modify: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0-h2/h2-schema.sql`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/MediaHookAuthenticationService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/MediaHookAuthenticationFilter.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/bean/MediaServer.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/storager/dao/MediaServerMapper.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/zlm/ZLMMediaServerStatusManager.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/abl/ABLMediaServerStatusManger.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Modify: `docker/media/config.ini`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/MediaHookAuthenticationFilterTest.java`

**Interfaces:**
- Produces: 已认证 `MediaNodePrincipal(mediaServerId,nodeType)`，只允许 `/index/hook/zlm/**` 或 `/index/hook/abl/**`。

- [x] **Step 1: 写伪造、错节点和重放失败测试**

覆盖缺 secret、错误 secret、body mediaServerId 与认证节点不一致、未知节点、停用节点、错误来源 IP、敏感 Hook timestamp/nonce 重放。

- [x] **Step 2: 生成并保存 Hook secret 哈希**

每节点生成 32 字节 secret，数据库只存 `hook_secret_hash`（HMAC-SHA-256 + server pepper）。ZLM 使用 `hook.admin_params` 写入 URL 编码后的 `mediaServerId` 和 `hookSecret`；ABL Hook URL 带同样的两个查询参数。轮换时先写新 hash，再更新节点，保留旧 hash 最长 5 分钟完成切换，随后删除。

媒体节点表增加等价字段：

```sql
alter table wvp_media_server add column hook_secret_hash varchar(64);
alter table wvp_media_server add column previous_hook_secret_hash varchar(64);
alter table wvp_media_server add column previous_hook_secret_expires_at timestamp;
alter table wvp_media_server add column hook_secret_version bigint not null default 0;
alter table wvp_media_server add column hook_allowed_cidrs varchar(2000);
```

API/日志 DTO 不返回两个 hash；Hook 参数、secret 和 ticket 必须加入访问日志脱敏规则。

- [x] **Step 3: 建立独立过滤链**

Hook 链不创建用户 Authentication，不接受 access-token 或 API Key。只有媒体节点身份验证通过才进入 listener；失败返回 HTTP 401 且不发布任何 Spring 事件。

- [x] **Step 4: 收紧网络入口**

`docker/media/config.ini` 不包含通用默认 secret；首次注册由 WVP 下发。生产配置支持 per-node CIDR，反向代理只允许媒体网段访问 `/index/hook/**`。

- [x] **Step 5: 运行测试（按用户要求不提交）**

Run: `mvn -Dtest=MediaHookAuthenticationFilterTest test`

Expected: PASS，未知或伪造节点无法触发 listener。

完成记录（2026-07-15）：Hook 链只允许 `/index/hook/zlm/**` 和 `/index/hook/abl/**`，查询节点 ID 必须与请求体一致；每节点凭证只保存 HMAC-SHA-256 哈希，支持 CIDR、nonce/timestamp 与短窗口 body 指纹防重放。密钥轮换保留旧哈希 5 分钟，过渡期内重试不会再次换代。Docker 初始媒体 Hook 关闭且无回调 URL，由 WVP 首次连接下发；WVP HTTP 端口仅在 `media-net` 暴露，公网 Nginx 拒绝 Hook 路径。`EndpointClassificationTest` 与 Hook 定向套件共 28 项通过，Compose 配置静态校验通过；未执行 Git 提交。

```bash
git add src/main/java/com/genersoft/iot/vmp/media src/main/java/com/genersoft/iot/vmp/conf/security src/main/java/com/genersoft/iot/vmp/storager/dao/MediaServerMapper.java docker/media/config.ini src/test/java/com/genersoft/iot/vmp/media/security
git commit -m "feat(media): authenticate media hook nodes"
```

---

### Task 5: 在 ZLM/ABL on_play 强制票据并修复 fail-open

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/AblSchemaResolver.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/zlm/ZLMHttpHookListener.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/abl/ABLHttpHookListener.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/media/abl/bean/hook/OnPlayABLHookParam.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/IMediaService.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/service/impl/MediaServiceImpl.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/com/genersoft/iot/vmp/media/zlm/ZLMHttpHookListenerTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/media/abl/ABLHttpHookListenerTest.java`

**Interfaces:**
- Consumes: `PlayTicketService.verify(MediaPlayHookRequest)`。
- Produces: ZLM/ABL 统一 ALLOW/DENY，拒绝不关闭其他合法流。

- [x] **Step 1: 写现有 fail-open 的回归测试**

ZLM：rtp app、缺 StreamAuthorityInfo、缺 callId/ticket、换 schema 均拒绝。ABL：节点不存在拒绝；鉴权失败返回失败且不能再记录成功；不能只 closeStreams 后返回 success。

- [x] **Step 2: ZLM 直接使用 schema**

从 OnPlayHookParam 读取 mediaServerId/app/stream/schema/ticket，构造 MediaPlayHookRequest；先由 Hook filter 验节点，再验证 ticket。内部不对外流使用单独 `InternalMediaTicket`，不能按 app 名硬编码放行。

- [x] **Step 3: ABL schema 显式映射**

ABL 参数只有 networkType，没有可靠 schema 字段。增加配置：

```yaml
authorization:
  media:
    abl-schema-by-network-type: {}
```

`AblSchemaResolver` 只接受运维显式配置的 networkType → FLV/WS_FLV/HLS/WEBRTC/RTSP/RTMP 映射；未知值在 audit 记录并走兼容，在 enforce 拒绝。上线 enforce 前从 audit 记录导出所有实际 networkType，配置并逐协议验证；禁止猜测或默认为 FLV。

- [x] **Step 4: 删除旧 authenticatePlay 放行条件**

`MediaServiceImpl.authenticatePlay(app,stream,callId)` 迁移为仅内部兼容适配，并在 enforce 不可调用。没有 ticket 的外部请求统一 DENY。

- [x] **Step 5: 运行测试（按用户要求不提交）**

Run: `mvn -Dtest=ZLMHttpHookListenerTest,ABLHttpHookListenerTest test`

Expected: 票据完整绑定才允许；所有未知情况 fail closed。

完成记录（2026-07-15）：ZLM `on_play` 不再按 `rtp`、流权限缓存或 app 名放行，必须同时匹配已认证媒体节点和绑定节点/app/stream/schema 的播放票据。ABL 只使用运维显式配置的 `networkType` 映射；未知类型在 audit 记录后仅走受限兼容，在 enforce 直接拒绝。节点不存在、票据异常和依赖失败均返回 401，拒绝路径不再调用 `closeStreams` 后伪装成功。ZLM、ABL 和 ABL 映射共 16 项定向测试通过；未执行 Git 提交。

```bash
git add src/main/java/com/genersoft/iot/vmp/media src/main/java/com/genersoft/iot/vmp/service src/main/resources/application.yml src/test/java/com/genersoft/iot/vmp/media
git commit -m "feat(media): enforce tickets in ZLM and ABL hooks"
```

---

### Task 6: 让所有前端播放器统一申请和续领票据

**Files:**
- Create: `web/src/api/mediaAccess.js`
- Create: `web/src/utils/mediaSource.js`
- Modify: `web/src/views/common/playerTabs.vue`
- Modify: `web/src/views/common/streamMediaPanel.vue`
- Modify: `web/src/views/device/dialog/devicePlayer.vue`
- Modify: `web/src/views/channel/player.vue`
- Modify: `web/src/views/jtDevice/dialog/jtDevicePlayer.vue`
- Modify: `web/src/views/streamProxy/player.vue`
- Modify: `web/src/views/streamPush/player.vue`
- Modify: `web/src/views/live/index.vue`
- Modify: `web/src/views/channel/audioTalk.vue`
- Modify: `web/src/views/cloudRecord/player.vue`
- Modify: `web/src/views/cloudRecord/playerDialog.vue`
- Test: `web/tests/unit/views/common/playerTabs.spec.js`
- Test: `web/tests/unit/utils/mediaSource.spec.js`

**Interfaces:**
- Consumes: PlayableStreamRef 和 `POST /api/media/play-ticket`。
- Produces: `setStreamInfo(playableRef)`，不再接受长期 URL 集合。

- [x] **Step 1: 写协议切换与竞态失败测试**

覆盖首次播放申请票据、切 FLV/HLS/WebRTC、票据过期重领、播放器重连、快速切换两条流取消旧请求、403 停止重试、组件销毁取消请求。

- [x] **Step 2: 实现集中 mediaSource**

```javascript
export async function resolveMediaSource(playableRef, schema, cancelToken) {
  const response = await issuePlayTicket({
    resourceType: playableRef.resourceType,
    resourceId: playableRef.resourceId,
    schema
  }, cancelToken)
  return response.data.sources[schema]
}
```

不得缓存超过 ticket.expiresAt；不得把 ticket 写 Cookie/localStorage/Vuex 持久层。

- [x] **Step 3: 迁移中心组件和所有调用点**

以 `playerTabs.vue` 为唯一协议选择中心。搜索所有 `setStreamInfo(` 和 StreamContent URL 字段调用，逐个改为资源引用；`streamMediaPanel.vue` 不再展示/复制 RTSP/RTMP/FLV 长期地址，可展示“复制 120 秒临时地址”且重新申请票据。

- [x] **Step 4: 运行测试（按用户要求不提交）**

Run: `cd web && npm run test:unit -- --runInBand tests/unit/views/common/playerTabs.spec.js tests/unit/utils/mediaSource.spec.js`

Expected: 2 个 suite PASS。

完成记录（2026-07-15）：`playerTabs.vue` 只接受 `PlayableStreamRef`，Jessibuca、H265web、WebRTC 分别申请 WS-FLV、HLS、WebRTC 票据；统一处理过期前续领、播放器重连、快速切流取消、迟到响应隔离、403 停止重试和组件销毁。所有点播、回放、云录像、告警及对讲预览调用点已迁移，长期 URL 字段只剩 Task 7 待替换的旧分享页和明确的浏览器发布端点。设备回传音频使用独立 `AUDIO_TALK` 绑定签发播放票据，浏览器上行仅返回 `type=push` 的 `MediaPublishEndpoint`。后端定向测试 15 项、前端 2 个 suite 共 12 项通过，目标文件 ESLint 0 error；未执行 Git 提交。

```bash
git add web/src/api/mediaAccess.js web/src/utils/mediaSource.js web/src/views web/tests/unit
git commit -m "feat(web): play all media through renewable tickets"
```

---

### Task 7: 实现受控分享和 shareId 页面

**Files:**
- Modify: `数据库/2.8.0/初始化-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/初始化-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-kingbase-2.8.0.sql`
- Modify: `数据库/2.8.0-h2/h2-schema.sql`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/ShareService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/impl/ShareServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/storager/dao/StreamShareMapper.java`
- Create: `src/main/java/com/genersoft/iot/vmp/vmanager/media/StreamShareController.java`
- Modify: `web/src/views/common/share.vue`
- Modify: `web/src/router/index.js`
- Modify: `web/src/permission.js`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/StreamShareServiceTest.java`
- Test: `web/tests/unit/views/common/share.spec.js`

**Interfaces:**
- Produces: `POST /api/media/share`、`DELETE /api/media/share/{shareId}`。
- Produces: `POST /api/public/share/{shareId}/play-ticket`，可带 passcode。

- [x] **Step 1: 写分享生命周期失败测试**

覆盖创建者资源权限、过期、撤销、次数耗尽、错误提取码、并发最后一次访问、权限收回后是否按策略撤销。默认策略为创建者权限收回后分享立即失效。

- [x] **Step 2: 创建分享表和原子次数更新**

分享 ID 使用 16 字节以上随机 base64url；passcode 用 BCrypt。兑换时条件更新：

```sql
create table wvp_stream_share (
  share_id varchar(64) primary key,
  creator_user_id bigint not null,
  resource_type varchar(32) not null,
  resource_id bigint not null,
  expires_at timestamp not null,
  max_views bigint,
  view_count bigint not null default 0,
  passcode_hash varchar(100),
  status varchar(16) not null,
  authz_version bigint not null,
  create_time timestamp not null,
  update_time timestamp not null,
  foreign key (creator_user_id) references wvp_user(id)
);

create index idx_wvp_stream_share_creator
  on wvp_stream_share(creator_user_id, status, expires_at);
```

兑换使用原子条件更新：

```sql
update wvp_stream_share
set view_count = view_count + 1
where share_id = #{shareId}
  and status = 'ACTIVE'
  and expires_at > current_timestamp
  and (max_views is null or view_count < max_views)
```

受影响行数为 0 即拒绝，避免并发超发。

- [x] **Step 3: 改分享路由**

删除旧式 `/#/play/share?url=https%3A%2F%2Fmedia.example%2Flive.flv` 路由，只保留 `/play/share/:shareId`。前端白名单按 route meta/name 判断，不能用精确 path 匹配动态参数；页面无法读取原始 URL，只得到 share ticketed source。

- [x] **Step 4: 运行测试（按用户要求不提交）**

Run: `mvn -Dtest=StreamShareServiceTest test`

Run: `cd web && npm run test:unit -- --runInBand tests/unit/views/common/share.spec.js`

Expected: 后端和前端测试均 PASS。

完成记录（2026-07-15）：分享创建按创建者当前资源权限和 `video:stream:share` 权限校验，shareId 使用 24 字节随机 base64url，提取码仅保存 BCrypt 哈希；兑换通过条件更新原子扣减次数，并在每次签发短票据前复核创建者 `authzVersion`。支持到期、撤销、次数耗尽、错误提取码、并发最后一次访问和权限收回立即失效。公开页只接受 `/play/share/:shareId`，不读取原始 URL；分享管理面板只提交稳定 `PlayableStreamRef`，支持有效期、次数、提取码和撤销。相关后端 23 项、前端 4 个 suite 共 19 项通过，长期 URL 分享静态扫描无命中；未执行 Git 提交。

```bash
git add 数据库/2.8.0 数据库/2.8.0-h2 src/main/java/com/genersoft/iot/vmp/media/security src/main/java/com/genersoft/iot/vmp/storager/dao/StreamShareMapper.java src/main/java/com/genersoft/iot/vmp/vmanager/media web/src web/tests/unit/views/common/share.spec.js
git commit -m "feat(media): add revocable stream shares"
```

---

### Task 8: 为录像、快照和文件下载增加票据并收紧 Nginx

**Files:**
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/DownloadTicketService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/media/security/impl/DownloadTicketServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/vmanager/media/SecuredDownloadController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/cloudRecord/CloudRecordController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/vmanager/alarm/AlarmController.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceQuery.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078Controller.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Modify: `docker/nginx/templates/nginx.conf.template`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/DownloadTicketServiceTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/media/security/SecuredDownloadControllerTest.java`

**Interfaces:**
- Produces: `POST /api/media/download-ticket`，请求只含 record/snapshot ID。
- Produces: `GET /api/media/download/{ticket}`，一次或有限次数使用。

- [x] **Step 1: 写换文件和路径穿越失败测试**

覆盖 ticket 过期、换 recordId、换文件、`../`、绝对路径、符号链接逃逸、批量 ZIP 混入无权 ID、远程 ZLM downloadFile URL 替换。

- [x] **Step 2: 服务端从记录 ID 解析规范路径**

路径先 `toRealPath()`，再验证 `resolved.startsWith(allowedRoot.toRealPath())`；客户端文件名只用于经过清洗的 Content-Disposition，不参与磁盘定位。下载 ticket Redis 值绑定主体、permission、resource、规范路径、次数和过期时间。

- [x] **Step 3: 关闭匿名和直通代理**

从 WebSecurity 白名单删除 snap、alarm snap、JT download/snap、record_proxy。Nginx 删除公开 `/mediaserver/api/downloadFile` 直通；改为 WVP 验证后使用内部 `X-Accel-Redirect` location，内部 location 设置 `internal;`，外部请求返回 404。

- [x] **Step 4: 运行测试；按用户要求不提交**

Run: `mvn -Dtest=DownloadTicketServiceTest,SecuredDownloadControllerTest test`

Expected: PASS，所有下载必须从受控记录 ID 和有效票据到达文件。

```bash
git add src/main/java/com/genersoft/iot/vmp/media/security src/main/java/com/genersoft/iot/vmp/vmanager src/main/java/com/genersoft/iot/vmp/gb28181/controller/DeviceQuery.java src/main/java/com/genersoft/iot/vmp/jt1078/controller/JT1078Controller.java src/main/java/com/genersoft/iot/vmp/conf/security docker/nginx/templates/nginx.conf.template src/test/java/com/genersoft/iot/vmp/media/security
git commit -m "feat(media): secure snapshots and downloads"
```

完成记录（2026-07-16）：云录像单文件/批量 ZIP、告警快照、GB/JT 快照、GB/JT 历史录像均已改为资源绑定的一次性 Redis 票据；客户端不能提交文件路径，GB 历史录像还会校验录像来源与已授权通道一致。路径解析与发送前均执行规范根目录校验，覆盖 `../`、绝对路径、符号链接逃逸、消费后换文件、无权批量成员和远程 ZLM 地址不外泄。WebSecurity 只匿名放行票据消费，Nginx 已删除 `record_proxy`、`mediaserver/api/downloadFile`、`mp4_record` 公开代理并使用 `internal` 的 `X-Accel-Redirect` 位置。后端 8 个定向测试类共 48 项通过，前端下载工具与媒体源 2 个 Jest suite 共 9 项通过，相关 ESLint 0 error，Docker Compose 静态配置校验通过；未启动项目服务、未执行 Git 提交。

---

### Task 9: 认证日志 WebSocket 并隔离内部身份

**Files:**
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/webLog/LogChannel.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/websocket/WebSocketConfig.java`
- Create: `src/main/java/com/genersoft/iot/vmp/conf/websocket/AuthenticatedWebSocketConfigurator.java`
- Create: `src/main/java/com/genersoft/iot/vmp/conf/websocket/LogWebSocketTicketService.java`
- Create: `src/main/java/com/genersoft/iot/vmp/conf/websocket/LogWebSocketTicketServiceImpl.java`
- Create: `src/main/java/com/genersoft/iot/vmp/conf/websocket/RedisLogWebSocketTicketStore.java`
- Create: `src/main/java/com/genersoft/iot/vmp/vmanager/log/LogWebSocketTicketController.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/internal/InternalPrincipal.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/internal/InternalInvocationGuard.java`
- Create: `src/main/java/com/genersoft/iot/vmp/auth/internal/InternalInvocationAspect.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/gb28181/transmit/SIPProcessorObserver.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/jt1078/codec/decode/Jt808Decoder.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/redis/RedisRpcConfig.java`
- Modify: `src/main/java/com/genersoft/iot/vmp/conf/security/WebSecurityConfig.java`
- Modify: `docker/nginx/templates/nginx.conf.template`
- Modify: `web/src/views/operations/showLog.vue`
- Create: `web/src/utils/logWebSocket.js`
- Test: `src/test/java/com/genersoft/iot/vmp/conf/websocket/LogWebSocketAuthenticationTest.java`
- Test: `src/test/java/com/genersoft/iot/vmp/auth/internal/InternalInvocationGuardTest.java`
- Test: `web/tests/unit/utils/logWebSocket.spec.js`

**Interfaces:**
- Produces: WebSocket 握手后保存 AuthPrincipal；日志读取要求 `operations:log:view`。
- Produces: InternalPrincipal 只能由受信协议/RPC/定时入口创建。

- [x] **Step 1: 写握手失败测试**

无 token、query token、过期 token、缺 log 权限均拒绝升级；合法 Header/Subprotocol token 成功且服务端不回显完整 token。连接建立后 tokenVersion 变化主动关闭。

- [x] **Step 2: 实现一次性 WebSocket 握手票据**

浏览器先通过受保护 HTTP API 申请 30 秒、单次使用的 websocket ticket，再通过 Sec-WebSocket-Protocol 发送；不直接发送长期 access-token。Configurator 消费后把 principal 放入 endpoint session userProperties。

- [x] **Step 3: 约束 InternalPrincipal**

SIP、JT、Redis RPC、媒体 Hook 和定时任务使用受控工厂创建 InternalPrincipal。普通 HTTP filter 无法根据请求参数构造；内部用例入口检查调用来源类型，不允许调用用户管理或资源配置用例。

- [x] **Step 4: 运行测试；按用户要求不提交**

Run: `mvn -Dtest=LogWebSocketAuthenticationTest,InternalInvocationGuardTest test`

Expected: PASS，日志 WebSocket 和内部身份均无外部伪造路径。

完成记录（2026-07-16）：实时日志改为先经受保护 HTTP 接口申请 30 秒、单次消费的 Redis 握手票据，再使用 `Sec-WebSocket-Protocol` 传递；服务端只协商并回显固定的 `wvp-log-v1`，拒绝 query token、长期 JWT、过期/重放票据和缺权限主体，连接建立后 tokenVersion 变化会主动关闭。SIP、JT1078、Redis RPC、已认证媒体 Hook 与定时任务分别建立线程作用域的最小 `InternalPrincipal`，普通 HTTP 请求和 Filter 无法构造，跨来源嵌套不能提权，用户/角色/部门/API Key 管理及视频资源组配置入口明确拒绝内部主体。后端相关 7 个测试类共 41 项通过，前端 Jest 3 项通过，相关 ESLint 0 问题，Docker Compose 与 Nginx 配置静态解析通过；未启动项目服务或容器、未执行 Git 提交。

---

### Task 10: 增加四类数据库契约测试和迁移核对

**Files:**
- Modify: `pom.xml`
- Create: `src/test/java/com/genersoft/iot/vmp/migration/RbacMySqlMigrationTest.java`
- Create: `src/test/java/com/genersoft/iot/vmp/migration/RbacPostgreSqlMigrationTest.java`
- Create: `src/test/java/com/genersoft/iot/vmp/migration/RbacKingbaseMigrationTest.java`
- Create: `src/test/java/com/genersoft/iot/vmp/migration/MediaAuthorizationSchemaContractTest.java`
- Modify: `docker/mysql/db/wvp.sql`
- Create: `docs/operations/2.8.0-migration-runbook.md`

**Interfaces:**
- Produces: Maven profile `db-contract` 和 `kingbase-contract`。
- Produces: 初始化、2.7.4→2.8.0 expand、数据核对、contract 和回滚命令。

- [x] **Step 1: 增加测试依赖和 profile**

在 Spring Boot BOM 管理下增加 test scope 的 `org.testcontainers:junit-jupiter`、`mysql`、`postgresql`。`db-contract` 启用 MySQL/PostgreSQL 测试；`kingbase-contract` 只在三个 WVP_KINGBASE 环境变量齐全时执行外部数据库测试。

- [x] **Step 2: 写同一逻辑契约**

四类数据库均验证：旧用户角色数量回填相等、root closure、资源组 closure、唯一键、索引、super_admin flag、API Key hash/scope、云录像来源、sequence/自增下一个 ID、expand 可由旧应用读取旧表。

- [x] **Step 3: 写 runbook 的可执行核对 SQL**

至少包含：

```sql
select count(*) from wvp_user;
select count(distinct user_id) from wvp_user_role_rel;
select count(*) from wvp_resource_group_item rgi
where not exists (select 1 from wvp_resource_group rg where rg.id=rgi.group_id);
select count(*) from wvp_cloud_record where channel_id is null and source_type <> 'LEGACY_UNBOUND';
select count(*) from wvp_user_api_key where status='LEGACY';
```

每条写明期望值和失败时回退动作。contract 前生成数据库快照并校验可恢复。

- [x] **Step 4: 执行数据库契约**

Run: `mvn -Pdb-contract -Dtest=RbacMySqlMigrationTest,RbacPostgreSqlMigrationTest,MediaAuthorizationSchemaContractTest test`

Expected: MySQL/PostgreSQL 容器测试 PASS。

Prerequisite: 在受控 CI Secret 中预先导出非空的 `WVP_KINGBASE_JDBC_URL`、`WVP_KINGBASE_USERNAME`、`WVP_KINGBASE_PASSWORD`，URL 必须指向可销毁测试库。

Run: `mvn -Pkingbase-contract -Dtest=RbacKingbaseMigrationTest test`

Expected: 已提供专用测试库时 PASS；命令只能对可销毁的测试库执行，不得对生产库执行。

- [x] **Step 5: 按用户要求不提交并记录验证结果**

不执行 `git add`、`git commit` 或 `git push`；保留工作区改动供用户检查。

完成记录（2026-07-16）：增加 Testcontainers MySQL/PostgreSQL 契约、H2 静态契约和环境变量门禁的 KingbaseES 外部测试夹具；修正 Docker MySQL 初始化链、三类数据库 expand 脚本的幂等性、自增/sequence 与角色关系迁移。MySQL 8.4.8、PostgreSQL 16 和 H2 共 4 项契约测试通过，覆盖 fresh init、2.7.4→2.8.0 expand、contract 前置拒绝、contract 重复执行、最终关系表/兼容视图读写与 ID 连续性。KingbaseES 命令执行 1 项、因未提供可销毁测试库环境而跳过，不能记为 KingbaseES 正式通过。临时数据库容器已由 Testcontainers 清理；未启动 WVP 业务服务，未执行 Git 提交或推送。

---

### Task 11: 灰度到 enforce、清理 LEGACY 并执行 contract

**Files:**
- Create: `docs/operations/2.8.0-authorization-rollout.md`
- Create: `src/test/java/com/genersoft/iot/vmp/security/AuthorizationEnforceReadinessTest.java`
- Modify: `docs/security/endpoint-permission-matrix.md`
- Modify: `docs/security/video-resource-authorization-matrix.md`
- Modify: `数据库/2.8.0/更新-mysql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-postgresql-2.8.0.sql`
- Modify: `数据库/2.8.0/更新-kingbase-2.8.0.sql`

**Interfaces:**
- Produces: 从 audit 到 enforce 的硬门禁；通过后 contract 到最终表名。

- [x] **Step 1: 写 readiness 失败测试**

测试读取配置/数据库/端点矩阵并在以下任一条件失败：未分类端点、LEGACY_AUDIT 资源端点、旧 URL 响应启用、未映射 ABL networkType、未配置节点 hook secret、LEGACY API Key 超期、未绑定云录像、公开下载路径、模式 disabled。

- [x] **Step 2: 固化灰度顺序和生产执行门禁**

Runbook 固定顺序：

```text
数据库快照
→ expand schema
→ 双写兼容后端
→ 配置角色/部门/资源组/API Key scope
→ authorization.mode=audit
→ 收集一个完整业务周期
→ Hook 身份 audit
→ 前端票据双轨
→ 关闭长期 URL
→ authorization.mode=enforce
→ 观察并处理拒绝
→ 停用 LEGACY API Key
→ 停机 contract
```

- [x] **Step 3: 执行全量测试门禁**

Run: `mvn test`

Expected: 全部测试 PASS，Tests run 大于 0。

Run: `mvn verify`

Expected: 构建和验证 PASS。

Run: `cd web && npm run test:ci`

Expected: lint 和 Jest 全部 PASS。

- [x] **Step 4: 执行静态泄漏检查**

```bash
rg "\?url=|getParameter\(JwtUtils.getHeader|api_key\s*[),]|authenticatePlay\(" src web
rg "permitAll|defaultExcludes" src/main/java/com/genersoft/iot/vmp/conf/security
rg "mediaserver/api/downloadFile|location /record_proxy|location \^~ /mp4_record" docker/nginx
```

Expected: 不存在长期 URL 分享、URL access-token、明文 Key 查询、旧 authenticatePlay 放行或公开媒体下载 location；所有 permitAll 均有矩阵中的 Public/MediaHook 解释。

- [x] **Step 5: 实现并验证 contract；按用户要求不提交**

生产执行时必须先停机并确认快照可恢复，再删除旧角色主表，将 `wvp_user_role_rel` 重命名为最终 `wvp_user_role`，校正 MySQL 自增/PostgreSQL 与 Kingbase sequence，启动新版本执行只读 smoke 验证。contract 失败立即恢复快照，不尝试混合旧应用与新表名。

本计划只在可销毁的 MySQL/PostgreSQL 测试容器中执行 contract；不连接生产数据库，不替用户切换生产 `enforce`，不执行 `git add`、`git commit` 或 `git push`。

完成记录（2026-07-16）：新增仓库级 enforce readiness 门禁，覆盖 disabled、未分类端点、`LEGACY_AUDIT`、URL token、长期媒体响应、ABL 未映射、Hook 密钥缺失、超期 LEGACY API Key、未绑定云录像和公开下载路径；补齐 audit→enforce→LEGACY 清零→停机 contract 的操作手册和三类数据库独立 contract 脚本。后端 `mvn test` 与 `mvn verify` 均通过，默认套件实际执行 617 项、0 失败、0 错误、1 项 KingbaseES 环境跳过；前端 27 个 Jest 套件、126 项测试通过，ESLint 0 error（项目既有 warning 666 条）；MySQL/PostgreSQL/H2 数据库契约 4 项通过；Compose 配置、差异格式和三组安全静态检查通过。生产灰度、真实业务周期、生产快照恢复、生产 enforce 和生产 contract 仍必须由上线责任人按手册执行并签字；未启动业务服务，未执行 Git 提交或推送。

## 完成定义

- 用户 JWT、API Key、媒体节点和内部任务是四种明确主体，不混用过滤器。
- API Key permission/resource scope 均不能扩大所属用户当前范围。
- 所有播放协议使用绑定资源和权限版本的短期 ticket。
- ZLM/ABL Hook 先验证节点身份，再验证 ticket；未知情况拒绝。
- 点播和媒体查询响应不包含长期 URL，分享只使用 shareId。
- 快照、录像、ZIP、JT 下载和日志 WebSocket 不再匿名。
- MySQL、PostgreSQL、H2 的初始化和升级契约已验证通过；KingbaseES 测试夹具和强制门禁已具备，涉及 KingbaseES 的发布必须在可销毁专用库补跑并通过。
- readiness 门禁通过后才允许 `authorization.mode=enforce`。
- 进入 enforce 时 LEGACY API Key 只能为零或处于批准的短兼容期；进入 contract 前必须归零，且长期 URL 双轨已关闭。
- contract 前有可恢复快照，contract 后最终角色关系表名为 `wvp_user_role`。
