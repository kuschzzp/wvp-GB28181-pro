# HTTP 端点权限矩阵

> 本文档由 `src/test/resources/security/controller-permission-matrix.csv` 生成。CSV 是机器校验的权威来源；`EndpointClassificationTest` 会双向比对 Spring MVC 实际映射，禁止遗漏、失效、重复端点、虚构解析器，以及 `REQUIRE_RESOURCE + LEGACY_AUDIT` 组合。

## 阶段说明

- 计划 3 的 Task 1～11 已完成自动化阶段验收；端点清零工作当前覆盖 445 个 HTTP Handler：资源端点仅引用代码中真实注册的 4 类解析器，平台/媒体节点/行政区划/业务分组等全局配置端点回归功能权限分类。
- 设备、通道、JT1078、推拉流、录像、告警、位置、平台共享、自定义摄像头、RTP/PS 发流、媒体信息、快照、活动 SSRC 和录像合并任务均已接入 SQL 范围或对象门禁；DeviceControl 的 SIP 控制指令也会先授权目标设备/通道。
- 可读的逐资源端点策略见 [视频资源授权矩阵](video-resource-authorization-matrix.md)，其中列明拒绝状态、列表范围和批量策略，并由独立测试与 CSV 保持一致。
- 下载、快照和录像文件已改为资源绑定的一次性短票据；公开入口只消费票据，票据签发仍要求登录并执行功能权限与对象范围校验。矩阵双向校验、计划 8 的 48 项定向回归，以及计划 11 的仓库级全量门禁均已于 2026-07-16 通过；生产数据阈值仍须按操作手册独立核对。
- `MEDIA_HOOK` 已进入最高优先级独立媒体节点认证链，校验节点密钥、类型、可选 CIDR 与重放保护，不接受用户 JWT/API Key。
- `DEPRECATED` 仅表示兼容入口仍处于 Sunset 窗口，不代表可以绕过认证或授权。

## 分类统计

| Classification | 数量 | 当前行为 |
|---|---:|---|
| `AUTHENTICATED` | 8 | 要求有效身份 |
| `MEDIA_HOOK` | 22 | 独立媒体节点认证链 |
| `PUBLIC` | 4 | 显式匿名入口 |
| `REQUIRE_PERMISSION` | 128 | 校验功能权限 |
| `REQUIRE_RESOURCE` | 283 | 校验功能权限并执行视频范围/对象门禁 |
| **合计** | **445** | — |

## 兼容状态统计

| Compatibility Status | 数量 |
|---|---:|
| `ACTIVE` | 417 |
| `DEPRECATED` | 28 |

## enforce readiness 口径

`AuthorizationEnforceReadinessTest` 与 `EndpointClassificationTest` 共同构成仓库门禁：前者读取配置、H2 数据契约、CSV 与 Nginx，后者双向发现实际 Spring MVC Handler。进入 `authorization.mode=enforce` 前必须同时满足：未分类端点为 0、`REQUIRE_RESOURCE + LEGACY_AUDIT` 为 0、旧 URL token 与长期媒体 URL 响应均关闭、未映射 ABL networkType 为 0、未配置 Hook 密钥的媒体节点为 0、超期 LEGACY API Key 为 0、未绑定云录像为 0、公开媒体文件 location 为 0。

2026-07-16 仓库基线已通过 `mvn test`、`mvn verify` 和 readiness 定向测试；该结论只证明代码、测试配置、H2 夹具、矩阵与 Nginx 模板一致，不替代生产数据库 SQL、完整业务周期、媒体节点密钥轮换或上线签字。

`WebSecurityConfig` 中的 `permitAll` 只承担以下入口，不改变控制器授权分类：

- CORS 预检、静态文件和可配置的接口文档资源；
- 登录入口；
- 公开分享的短期播放票据消费入口；
- 一次性下载票据消费入口 `/api/media/download/*`；
- 日志 WebSocket 的握手入口 `/channel/log`，真正身份来自一次性 Subprotocol 票据；
- liveness 探针；
- 22 个 `MEDIA_HOOK` 路径由更高优先级的独立媒体节点认证链处理，不接受用户 JWT 或 API Key。

因此，`permitAll` 不等于业务匿名：下载票据、WebSocket 票据和媒体 Hook 在进入业务处理前都有各自的 fail-closed 身份校验。完整灰度、核对和回滚步骤见 [2.8.0 授权灰度操作手册](../operations/2.8.0-authorization-rollout.md)。

## 完整矩阵

| HTTP Method | Path | Controller Method | Classification | Permission Code | Resource Resolver | Compatibility Status |
|---|---|---|---|---|---|---|
| `POST` | `/api/auth/login` | `AuthController#login` | `PUBLIC` | — | — | `ACTIVE` |
| `GET` | `/api/auth/profile` | `AuthController#profile` | `AUTHENTICATED` | — | — | `ACTIVE` |
| `GET` | `/api/menu/routers` | `MenuController#routers` | `AUTHENTICATED` | — | — | `ACTIVE` |
| `POST` | `/api/system/dept` | `DepartmentController#create` | `REQUIRE_PERMISSION` | `system:dept:add` | — | `ACTIVE` |
| `DELETE` | `/api/system/dept/{id}` | `DepartmentController#delete` | `REQUIRE_PERMISSION` | `system:dept:remove` | — | `ACTIVE` |
| `GET` | `/api/system/dept/tree` | `DepartmentController#tree` | `REQUIRE_PERMISSION` | `system:dept:list` | — | `ACTIVE` |
| `PUT` | `/api/system/dept/{id}` | `DepartmentController#update` | `REQUIRE_PERMISSION` | `system:dept:edit` | — | `ACTIVE` |
| `POST` | `/api/system/menu` | `SystemMenuController#create` | `REQUIRE_PERMISSION` | `system:menu:add` | — | `ACTIVE` |
| `DELETE` | `/api/system/menu/{id}` | `SystemMenuController#delete` | `REQUIRE_PERMISSION` | `system:menu:remove` | — | `ACTIVE` |
| `GET` | `/api/system/menu/{id}` | `SystemMenuController#detail` | `REQUIRE_PERMISSION` | `system:menu:list` | — | `ACTIVE` |
| `GET` | `/api/system/menu` | `SystemMenuController#list` | `REQUIRE_PERMISSION` | `system:menu:list` | — | `ACTIVE` |
| `PUT` | `/api/system/menu/{id}` | `SystemMenuController#update` | `REQUIRE_PERMISSION` | `system:menu:edit` | — | `ACTIVE` |
| `POST` | `/api/system/role` | `SystemRoleController#create` | `REQUIRE_PERMISSION` | `system:role:add` | — | `ACTIVE` |
| `PUT` | `/api/system/role/{id}/status` | `SystemRoleController#changeStatus` | `REQUIRE_PERMISSION` | `system:role:changeStatus` | — | `ACTIVE` |
| `DELETE` | `/api/system/role/{id}` | `SystemRoleController#delete` | `REQUIRE_PERMISSION` | `system:role:remove` | — | `ACTIVE` |
| `GET` | `/api/system/role/{id}` | `SystemRoleController#detail` | `REQUIRE_PERMISSION` | `system:role:list` | — | `ACTIVE` |
| `GET` | `/api/system/role` | `SystemRoleController#list` | `REQUIRE_PERMISSION` | `system:role:list` | — | `ACTIVE` |
| `GET` | `/api/system/role/references` | `SystemRoleController#references` | `REQUIRE_PERMISSION` | `system:role:list` | — | `ACTIVE` |
| `PUT` | `/api/system/role/{id}` | `SystemRoleController#update` | `REQUIRE_PERMISSION` | `system:role:edit` | — | `ACTIVE` |
| `PUT` | `/api/system/user/{id}/roles` | `SystemUserController#assignRoles` | `REQUIRE_PERMISSION` | `system:role:assign` | — | `ACTIVE` |
| `PUT` | `/api/system/user/profile/password` | `SystemUserController#changeOwnPassword` | `AUTHENTICATED` | — | — | `ACTIVE` |
| `PUT` | `/api/system/user/{id}/status` | `SystemUserController#changeStatus` | `REQUIRE_PERMISSION` | `system:user:changeStatus` | — | `ACTIVE` |
| `POST` | `/api/system/user` | `SystemUserController#create` | `REQUIRE_PERMISSION` | `system:user:add` | — | `ACTIVE` |
| `DELETE` | `/api/system/user/{id}` | `SystemUserController#delete` | `REQUIRE_PERMISSION` | `system:user:remove` | — | `ACTIVE` |
| `GET` | `/api/system/user/{id}` | `SystemUserController#detail` | `REQUIRE_PERMISSION` | `system:user:list` | — | `ACTIVE` |
| `GET` | `/api/system/user` | `SystemUserController#query` | `REQUIRE_PERMISSION` | `system:user:list` | — | `ACTIVE` |
| `GET` | `/api/system/user/references` | `SystemUserController#references` | `REQUIRE_PERMISSION` | `system:user:list` | — | `ACTIVE` |
| `PUT` | `/api/system/user/{id}/password` | `SystemUserController#resetPassword` | `REQUIRE_PERMISSION` | `system:user:resetPwd` | — | `ACTIVE` |
| `PUT` | `/api/system/user/{id}` | `SystemUserController#update` | `REQUIRE_PERMISSION` | `system:user:edit` | — | `ACTIVE` |
| `PUT` | `/api/system/user/profile` | `SystemUserController#updateOwnProfile` | `AUTHENTICATED` | — | — | `ACTIVE` |
| `POST` | `/api/system/resource-group` | `ResourceGroupController#create` | `REQUIRE_PERMISSION` | `system:resource-group:add` | — | `ACTIVE` |
| `DELETE` | `/api/system/resource-group/{groupId}` | `ResourceGroupController#delete` | `REQUIRE_PERMISSION` | `system:resource-group:remove` | — | `ACTIVE` |
| `GET` | `/api/system/resource-group/{groupId}/items` | `ResourceGroupController#items` | `REQUIRE_PERMISSION` | `system:resource-group:list` | — | `ACTIVE` |
| `PUT` | `/api/system/resource-group/{groupId}/items` | `ResourceGroupController#replaceItems` | `REQUIRE_PERMISSION` | `system:resource-group:assign` | — | `ACTIVE` |
| `PUT` | `/api/system/resource-group/role/{roleId}` | `ResourceGroupController#replaceRoleGroups` | `REQUIRE_PERMISSION` | `system:resource-group:assign` | — | `ACTIVE` |
| `GET` | `/api/system/resource-group/role/{roleId}` | `ResourceGroupController#roleGroups` | `REQUIRE_PERMISSION` | `system:resource-group:list` | — | `ACTIVE` |
| `GET` | `/api/system/resource-group/tree` | `ResourceGroupController#tree` | `REQUIRE_PERMISSION` | `system:resource-group:list` | — | `ACTIVE` |
| `PUT` | `/api/system/resource-group/{groupId}` | `ResourceGroupController#update` | `REQUIRE_PERMISSION` | `system:resource-group:edit` | — | `ACTIVE` |
| `POST` | `/api/common/channel/add` | `ChannelController#add` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `POST` | `/api/common/channel/group/add` | `ChannelController#addChannelToGroup` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/group/device/add` | `ChannelController#addChannelToGroupByGbDevice` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/region/add` | `ChannelController#addChannelToRegion` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/region/device/add` | `ChannelController#addChannelToRegionByGbDevice` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/civilCode/unusual/clear` | `ChannelController#clearChannelCivilCode` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/parent/unusual/clear` | `ChannelController#clearChannelParent` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/map/thin/clear` | `ChannelController#clearThin` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/group/delete` | `ChannelController#deleteChannelToGroup` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/group/device/delete` | `ChannelController#deleteChannelToGroupByGbDevice` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/region/delete` | `ChannelController#deleteChannelToRegion` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/region/device/delete` | `ChannelController#deleteChannelToRegionByGbDevice` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/map/thin/draw` | `ChannelController#drawThin` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/type/list` | `ChannelController#getDeviceTypeList` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/common/channel/industry/list` | `ChannelController#getIndustryCodeList` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/common/channel/network/identification/list` | `ChannelController#getNetworkIdentificationTypeList` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/common/channel/one` | `ChannelController#getOne` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/map/thin/tile/{z}/{x}/{y}` | `ChannelController#getThinTile` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/map/tile/{z}/{x}/{y}` | `ChannelController#getTile` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback/pause` | `ChannelController#pausePlayback` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/play` | `ChannelController#play` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback` | `ChannelController#playback` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/list` | `ChannelController#queryList` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/civilcode/list` | `ChannelController#queryListByCivilCode` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/civilCode/unusual/list` | `ChannelController#queryListByCivilCodeForUnusual` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/parent/unusual/list` | `ChannelController#queryListByParentForUnusual` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/parent/list` | `ChannelController#queryListByParentId` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/map/list` | `ChannelController#queryListForMap` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback/query` | `ChannelController#queryRecord` | `REQUIRE_RESOURCE` | `video:record:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/reset` | `ChannelController#reset` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/map/reset-level` | `ChannelController#resetLevel` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback/resume` | `ChannelController#resumePlayback` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/map/thin/save` | `ChannelController#saveThin` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback/seek` | `ChannelController#seekPlayback` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback/speed` | `ChannelController#seekPlayback` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/broadcast/start` | `ChannelController#startBroadcast` | `REQUIRE_RESOURCE` | `video:channel:broadcast` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/talk/start` | `ChannelController#startTalk` | `REQUIRE_RESOURCE` | `video:channel:talk` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/broadcast/stop` | `ChannelController#stopBroadcast` | `REQUIRE_RESOURCE` | `video:channel:broadcast` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/play/stop` | `ChannelController#stopPlay` | `REQUIRE_RESOURCE` | `video:channel:stop` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/playback/stop` | `ChannelController#stopPlayback` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/talk/stop` | `ChannelController#stopTalk` | `REQUIRE_RESOURCE` | `video:channel:talk` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/map/thin/progress` | `ChannelController#thinProgress` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/common/channel/update` | `ChannelController#update` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/preset/add` | `ChannelFrontEndController#addPreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/tour/point/add` | `ChannelFrontEndController#addTourPoint` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/auxiliary` | `ChannelFrontEndController#auxiliarySwitch` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/preset/call` | `ChannelFrontEndController#callPreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/tour/point/delete` | `ChannelFrontEndController#deleteCruisePoint` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/preset/delete` | `ChannelFrontEndController#deletePreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/drag_zoom_in` | `ChannelFrontEndController#dragZoomIn` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/drag_zoom_out` | `ChannelFrontEndController#dragZoomOut` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/fi/focus` | `ChannelFrontEndController#focus` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/home_position` | `ChannelFrontEndController#homePosition` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/fi/iris` | `ChannelFrontEndController#iris` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/ptz` | `ChannelFrontEndController#ptz` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/preset/query` | `ChannelFrontEndController#queryPreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/tour/speed` | `ChannelFrontEndController#setCruiseSpeed` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/tour/time` | `ChannelFrontEndController#setCruiseTime` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/scan/set/left` | `ChannelFrontEndController#setScanLeft` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/scan/set/right` | `ChannelFrontEndController#setScanRight` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/scan/set/speed` | `ChannelFrontEndController#setScanSpeed` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/tour/start` | `ChannelFrontEndController#startCruise` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/scan/start` | `ChannelFrontEndController#startScan` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/tour/stop` | `ChannelFrontEndController#stopCruise` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/scan/stop` | `ChannelFrontEndController#stopScan` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/common/channel/front-end/wiper` | `ChannelFrontEndController#wiper` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/config/set/basicParam` | `DeviceConfig#homePositionApi` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/config/query/basicParam` | `DeviceConfig#queryBasicParam` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/config/query/svacDecodeConfig` | `DeviceConfig#querySVACDecodeConfig` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/config/query/svacEncodeConfig` | `DeviceConfig#querySVACEncodeConfig` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/config/query/videoParamOpt` | `DeviceConfig#queryVideoParamOpt` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/config/set/videoParamOpt` | `DeviceConfig#setVideoParamOpt` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/drag_zoom/zoom_in` | `DeviceControl#dragZoomIn` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/drag_zoom/zoom_out` | `DeviceControl#dragZoomOut` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/guard` | `DeviceControl#guardApi` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/home_position` | `DeviceControl#homePositionApi` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/i_frame` | `DeviceControl#iFrame` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/record` | `DeviceControl#recordApi` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/reset_alarm` | `DeviceControl#resetAlarm` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/control/teleboot/{deviceId}` | `DeviceControl#teleBootApi` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/device/query/device/add` | `DeviceQuery#addDevice` | `REQUIRE_PERMISSION` | `video:device:add` | — | `ACTIVE` |
| `GET` | `/api/device/query/alarm` | `DeviceQuery#alarmApi` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/device/query/channel/audio` | `DeviceQuery#changeAudio` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/devices/{deviceId}/channels` | `DeviceQuery#channels` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/device/query/devices/{deviceId}/delete` | `DeviceQuery#delete` | `REQUIRE_RESOURCE` | `video:device:remove` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/info` | `DeviceQuery#deviceInfo` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/devices/{deviceId}/status` | `DeviceQuery#deviceStatusApi` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/devices` | `DeviceQuery#devices` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/devices/{deviceId}` | `DeviceQuery#devices` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/devices/{deviceId}/sync` | `DeviceQuery#devicesSync` | `REQUIRE_RESOURCE` | `video:device:sync` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/channel/one` | `DeviceQuery#getChannel` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/statistics/keepalive` | `DeviceQuery#getKeepaliveTimeStatistics` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/channel/raw` | `DeviceQuery#getRawChannel` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/statistics/register` | `DeviceQuery#getRegisterTimeStatistics` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/snap/{deviceId}/{channelId}` | `DeviceQuery#getSnap` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/sync_status` | `DeviceQuery#getSyncStatus` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/{deviceId}/sync_status` | `DeviceQuery#getSyncStatusInPath` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/streams` | `DeviceQuery#streamChannels` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/sub_channels/{deviceId}/{channelId}/channels` | `DeviceQuery#subChannels` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/subscribe/alarm` | `DeviceQuery#subscribeAlarm` | `REQUIRE_RESOURCE` | `video:device:config` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/subscribe/catalog` | `DeviceQuery#subscribeCatalog` | `REQUIRE_RESOURCE` | `video:device:sync` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/device/query/subscribe/mobile-position` | `DeviceQuery#subscribeMobilePosition` | `REQUIRE_RESOURCE` | `video:position:subscribe` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/device/query/channel/stream/identification/update/` | `DeviceQuery#updateChannelStreamIdentification` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/device/query/device/update` | `DeviceQuery#updateDevice` | `REQUIRE_RESOURCE` | `video:device:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/device/query/transport/{deviceId}/{streamMode}` | `DeviceQuery#updateTransport` | `REQUIRE_RESOURCE` | `video:device:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/gb_record/download/start/{deviceId}/{channelId}` | `GBRecordController#download` | `REQUIRE_RESOURCE` | `video:record:download` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/gb_record/download/stop/{deviceId}/{channelId}/{stream}` | `GBRecordController#downloadStop` | `REQUIRE_RESOURCE` | `video:record:download` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/gb_record/download/progress/{deviceId}/{channelId}/{stream}` | `GBRecordController#getProgress` | `REQUIRE_RESOURCE` | `video:record:download` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/gb_record/query/{deviceId}/{channelId}` | `GBRecordController#recordinfo` | `REQUIRE_RESOURCE` | `video:record:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/group/add` | `GroupController#add` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `DELETE` | `/api/group/delete` | `GroupController#delete` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `GET` | `/api/group/path` | `GroupController#getPath` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/group/tree/list` | `GroupController#queryForTree` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/group/tree/query` | `GroupController#queryTree` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `POST` | `/api/group/update` | `GroupController#update` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `GET` | `/api/media/getPlayUrl` | `MediaController#getPlayUrl` | `REQUIRE_RESOURCE` | `video:channel:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/media/stream_info_by_app_and_stream` | `MediaController#getStreamInfoByAppAndStream` | `REQUIRE_RESOURCE` | `video:channel:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/position/latest` | `MobilePositionController#latestPosition` | `REQUIRE_RESOURCE` | `video:position:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/position/subscribe/{deviceId}` | `MobilePositionController#positionSubscribe` | `REQUIRE_RESOURCE` | `video:position:subscribe` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/position/history/{deviceId}` | `MobilePositionController#positions` | `REQUIRE_RESOURCE` | `video:position:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/position/realtime/{deviceId}` | `MobilePositionController#realTimePosition` | `REQUIRE_RESOURCE` | `video:position:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/platform/add` | `PlatformController#add` | `REQUIRE_PERMISSION` | `video:platform:manage` | — | `ACTIVE` |
| `POST` | `/api/platform/channel/add` | `PlatformController#addChannel` | `REQUIRE_RESOURCE` | `video:platform:share-channel` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/platform/channel/device/add` | `PlatformController#addChannelByDevice` | `REQUIRE_RESOURCE` | `video:platform:share-channel` | `ChannelResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/platform/channel/remove` | `PlatformController#delChannelForGB` | `REQUIRE_RESOURCE` | `video:platform:share-channel` | `ChannelResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/platform/delete` | `PlatformController#deletePlatform` | `REQUIRE_PERMISSION` | `video:platform:manage` | — | `ACTIVE` |
| `GET` | `/api/platform/exit/{serverGBId}` | `PlatformController#exitPlatform` | `REQUIRE_PERMISSION` | `video:platform:list` | — | `ACTIVE` |
| `GET` | `/api/platform/info/{id}` | `PlatformController#getPlatform` | `REQUIRE_PERMISSION` | `video:platform:list` | — | `ACTIVE` |
| `GET` | `/api/platform/query` | `PlatformController#platforms` | `REQUIRE_PERMISSION` | `video:platform:list` | — | `ACTIVE` |
| `GET` | `/api/platform/channel/push` | `PlatformController#pushChannel` | `REQUIRE_RESOURCE` | `video:platform:share-channel` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/platform/channel/list` | `PlatformController#queryChannelList` | `REQUIRE_RESOURCE` | `video:platform:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/platform/channel/device/remove` | `PlatformController#removeChannelByDevice` | `REQUIRE_RESOURCE` | `video:platform:share-channel` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/platform/server_config` | `PlatformController#serverConfig` | `REQUIRE_PERMISSION` | `video:platform:manage` | — | `ACTIVE` |
| `POST` | `/api/platform/channel/custom/update` | `PlatformController#updateCustomChannel` | `REQUIRE_RESOURCE` | `video:platform:share-channel` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/platform/update` | `PlatformController#updatePlatform` | `REQUIRE_PERMISSION` | `video:platform:manage` | — | `ACTIVE` |
| `GET` | `/api/play/broadcast/{deviceId}/{channelId}` | `PlayController#broadcastApi` | `REQUIRE_RESOURCE` | `video:channel:broadcast` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/play/ssrc` | `PlayController#getSSRC` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/play/snap` | `PlayController#getSnap` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/play/start/{deviceId}/{channelId}` | `PlayController#play` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/play/convertStop/{key}` | `PlayController#playConvertStop` | `REQUIRE_PERMISSION` | `video:channel:stop` | — | `DEPRECATED` |
| `GET` | `/api/play/stop/{deviceId}/{channelId}` | `PlayController#playStop` | `REQUIRE_RESOURCE` | `video:channel:stop` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/play/broadcast/stop/{deviceId}/{channelId}` | `PlayController#stopBroadcast` | `REQUIRE_RESOURCE` | `video:channel:broadcast` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/playback/resume/{streamId}` | `PlaybackController#playResume` | `REQUIRE_RESOURCE` | `video:record:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/playback/speed/{streamId}/{speed}` | `PlaybackController#playSpeed` | `REQUIRE_RESOURCE` | `video:record:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/playback/stop/{deviceId}/{channelId}/{stream}` | `PlaybackController#playStop` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/playback/pause/{streamId}` | `PlaybackController#playbackPause` | `REQUIRE_RESOURCE` | `video:record:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/playback/seek/{streamId}/{seekTime}` | `PlaybackController#playbackSeek` | `REQUIRE_RESOURCE` | `video:record:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/playback/start/{deviceId}/{channelId}` | `PlaybackController#start` | `REQUIRE_RESOURCE` | `video:record:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/cruise/point/add/{deviceId}/{channelId}` | `PtzController#addCruisePoint` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/preset/add/{deviceId}/{channelId}` | `PtzController#addPreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/auxiliary/{deviceId}/{channelId}` | `PtzController#auxiliarySwitch` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/preset/call/{deviceId}/{channelId}` | `PtzController#callPreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/cruise/point/delete/{deviceId}/{channelId}` | `PtzController#deleteCruisePoint` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/preset/delete/{deviceId}/{channelId}` | `PtzController#deletePreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/fi/focus/{deviceId}/{channelId}` | `PtzController#focus` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/common/{deviceId}/{channelId}` | `PtzController#frontEndCommand` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/fi/iris/{deviceId}/{channelId}` | `PtzController#iris` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/ptz/{deviceId}/{channelId}` | `PtzController#ptz` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/preset/query/{deviceId}/{channelId}` | `PtzController#queryPreset` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/cruise/speed/{deviceId}/{channelId}` | `PtzController#setCruiseSpeed` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/cruise/time/{deviceId}/{channelId}` | `PtzController#setCruiseTime` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/scan/set/left/{deviceId}/{channelId}` | `PtzController#setScanLeft` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/scan/set/right/{deviceId}/{channelId}` | `PtzController#setScanRight` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/scan/set/speed/{deviceId}/{channelId}` | `PtzController#setScanSpeed` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/cruise/start/{deviceId}/{channelId}` | `PtzController#startCruise` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/scan/start/{deviceId}/{channelId}` | `PtzController#startScan` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/cruise/stop/{deviceId}/{channelId}` | `PtzController#stopCruise` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/scan/stop/{deviceId}/{channelId}` | `PtzController#stopScan` | `REQUIRE_RESOURCE` | `video:ptz:cruise` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/front-end/wiper/{deviceId}/{channelId}` | `PtzController#wiper` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/region/add` | `RegionController#add` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `GET` | `/api/region/addByCivilCode` | `RegionController#addByCivilCode` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `DELETE` | `/api/region/delete` | `RegionController#delete` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `GET` | `/api/region/base/child/list` | `RegionController#getAllChild` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/description` | `RegionController#getDescription` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/path` | `RegionController#getPath` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/page/list` | `RegionController#query` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/tree/list` | `RegionController#queryForTree` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/one` | `RegionController#queryRegionByDeviceId` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/tree/query` | `RegionController#queryTree` | `REQUIRE_PERMISSION` | `video:channel:list` | — | `ACTIVE` |
| `GET` | `/api/region/sync` | `RegionController#sync` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `POST` | `/api/region/update` | `RegionController#update` | `REQUIRE_PERMISSION` | `video:channel:edit` | — | `ACTIVE` |
| `POST` | `/api/jt1078/area/circle/add` | `JT1078Controller#addAreaForCircle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/area/rectangle/add` | `JT1078Controller#addAreaForRectangle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/attribute` | `JT1078Controller#attribute` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/live/switch` | `JT1078Controller#changeStreamType` | `REQUIRE_RESOURCE` | `video:jt:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/config/get` | `JT1078Controller#config` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/confirmation-alarm-message` | `JT1078Controller#confirmationAlarmMessage` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/control/connection` | `JT1078Controller#connectionControl` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/live/continue` | `JT1078Controller#continueLive` | `REQUIRE_RESOURCE` | `video:jt:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/control/door` | `JT1078Controller#controlDoor` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/area/circle/delete` | `JT1078Controller#deleteAreaForCircle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/area/polygon/delete` | `JT1078Controller#deleteAreaForPolygon` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/area/rectangle/delete` | `JT1078Controller#deleteAreaForRectangle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/media/upload/one/delete` | `JT1078Controller#deleteOneMedia` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/route/delete` | `JT1078Controller#deleteRoute` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/area/circle/edit` | `JT1078Controller#editAreaForCircle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/area/rectangle/edit` | `JT1078Controller#editAreaForRectangle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/control/factory-reset` | `JT1078Controller#factoryResetControl` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/fill-light` | `JT1078Controller#fillLight` | `REQUIRE_RESOURCE` | `video:jt:ptz` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/playback/downloadUrl` | `JT1078Controller#getRecordTempUrl` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/link-detection` | `JT1078Controller#linkDetection` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/live/pause` | `JT1078Controller#pauseLive` | `REQUIRE_RESOURCE` | `video:jt:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/record/list` | `JT1078Controller#playbackList` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/ptz` | `JT1078Controller#ptz` | `REQUIRE_RESOURCE` | `video:jt:ptz` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/area/circle/query` | `JT1078Controller#queryAreaForCircle` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/area/polygon/query` | `JT1078Controller#queryAreaForPolygon` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/area/rectangle/query` | `JT1078Controller#queryAreaForRectangle` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/driver-information` | `JT1078Controller#queryDriverInformation` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/media/attribute` | `JT1078Controller#queryMediaAttribute` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/media/list` | `JT1078Controller#queryMediaData` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/position-info` | `JT1078Controller#queryPositionInfo` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/route/query` | `JT1078Controller#queryRoute` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/playback/control` | `JT1078Controller#recordControl` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/playback/start` | `JT1078Controller#recordLive` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/control/reset` | `JT1078Controller#resetControl` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/area/polygon/set` | `JT1078Controller#setAreaForPolygon` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/config/set` | `JT1078Controller#setConfig` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/set-phone-book` | `JT1078Controller#setPhoneBook` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/route/set` | `JT1078Controller#setRoute` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/shooting` | `JT1078Controller#shooting` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/snap` | `JT1078Controller#snap` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/live/start` | `JT1078Controller#startLive` | `REQUIRE_RESOURCE` | `video:jt:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/record/start` | `JT1078Controller#startRecord` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/talk/start` | `JT1078Controller#startTalk` | `REQUIRE_RESOURCE` | `video:jt:talk` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/live/stop` | `JT1078Controller#stopLive` | `REQUIRE_RESOURCE` | `video:jt:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/playback/stop` | `JT1078Controller#stopPlayback` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/record/stop` | `JT1078Controller#stopRecord` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/talk/stop` | `JT1078Controller#stopTalk` | `REQUIRE_RESOURCE` | `video:jt:talk` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/telephone-callback` | `JT1078Controller#telephoneCallback` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/control/temp-position-tracking` | `JT1078Controller#tempPositionTrackingControl` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/text-msg` | `JT1078Controller#textMessage` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/area/circle/update` | `JT1078Controller#updateAreaForCircle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/area/rectangle/update` | `JT1078Controller#updateAreaForRectangle` | `REQUIRE_RESOURCE` | `video:jt:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/media/upload/one/upload` | `JT1078Controller#uploadOneMedia` | `REQUIRE_RESOURCE` | `video:jt:record` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/wiper` | `JT1078Controller#wiper` | `REQUIRE_RESOURCE` | `video:jt:ptz` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/terminal/channel/add` | `JT1078TerminalController#addChannel` | `REQUIRE_RESOURCE` | `video:jt:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/jt1078/terminal/delete` | `JT1078TerminalController#addDevice` | `REQUIRE_RESOURCE` | `video:jt:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/terminal/add` | `JT1078TerminalController#addDevice` | `REQUIRE_PERMISSION` | `video:jt:edit` | — | `ACTIVE` |
| `DELETE` | `/api/jt1078/terminal/channel/delete` | `JT1078TerminalController#deleteChannel` | `REQUIRE_RESOURCE` | `video:jt:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/terminal/channel/one` | `JT1078TerminalController#getChannel` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/terminal/channel/list` | `JT1078TerminalController#getChannels` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/terminal/query` | `JT1078TerminalController#getDevice` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/jt1078/terminal/list` | `JT1078TerminalController#getDevices` | `REQUIRE_RESOURCE` | `video:jt:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/terminal/channel/update` | `JT1078TerminalController#updateChannel` | `REQUIRE_RESOURCE` | `video:jt:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/jt1078/terminal/update` | `JT1078TerminalController#updateDevice` | `REQUIRE_RESOURCE` | `video:jt:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/index/hook/abl/on_delete_record_mp4` | `ABLHttpHookListener#onDeleteRecordMp4` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_play` | `ABLHttpHookListener#onPlay` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_publish` | `ABLHttpHookListener#onPublish` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_record_mp4` | `ABLHttpHookListener#onRecordMp4` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_stream_disconnect` | `ABLHttpHookListener#onRecordMp4` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_record_progress` | `ABLHttpHookListener#onRecordProgress` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_server_keepalive` | `ABLHttpHookListener#onServerKeepalive` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_server_started` | `ABLHttpHookListener#onServerStarted` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_stream_arrive` | `ABLHttpHookListener#onStreamArrive` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_stream_none_reader` | `ABLHttpHookListener#onStreamNoneReader` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_stream_not_arrive` | `ABLHttpHookListener#onStreamNotArrive` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/abl/on_stream_not_found` | `ABLHttpHookListener#onStreamNotFound` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_play` | `ZLMHttpHookListener#onPlay` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_publish` | `ZLMHttpHookListener#onPublish` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_record_mp4` | `ZLMHttpHookListener#onRecordMp4` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_rtp_server_timeout` | `ZLMHttpHookListener#onRtpServerTimeout` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_send_rtp_stopped` | `ZLMHttpHookListener#onSendRtpStopped` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_server_keepalive` | `ZLMHttpHookListener#onServerKeepalive` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_server_started` | `ZLMHttpHookListener#onServerStarted` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_stream_changed` | `ZLMHttpHookListener#onStreamChanged` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_stream_none_reader` | `ZLMHttpHookListener#onStreamNoneReader` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/index/hook/zlm/on_stream_not_found` | `ZLMHttpHookListener#onStreamNotFound` | `MEDIA_HOOK` | — | — | `ACTIVE` |
| `POST` | `/api/proxy/add` | `StreamProxyController#add` | `REQUIRE_PERMISSION` | `video:proxy:add` | — | `ACTIVE` |
| `DELETE` | `/api/proxy/del` | `StreamProxyController#del` | `REQUIRE_RESOURCE` | `video:proxy:remove` | `StreamResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/proxy/delete` | `StreamProxyController#delte` | `REQUIRE_RESOURCE` | `video:proxy:remove` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/proxy/ffmpeg_cmd/list` | `StreamProxyController#getFFmpegCMDs` | `REQUIRE_PERMISSION` | `video:proxy:list` | — | `ACTIVE` |
| `GET` | `/api/proxy/list` | `StreamProxyController#list` | `REQUIRE_RESOURCE` | `video:proxy:list` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/proxy/one` | `StreamProxyController#one` | `REQUIRE_RESOURCE` | `video:proxy:list` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/proxy/start` | `StreamProxyController#start` | `REQUIRE_RESOURCE` | `video:proxy:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/proxy/stop` | `StreamProxyController#stop` | `REQUIRE_RESOURCE` | `video:proxy:stop` | `StreamResourceResolver` | `ACTIVE` |
| `POST` | `/api/proxy/update` | `StreamProxyController#update` | `REQUIRE_RESOURCE` | `video:proxy:edit` | `StreamResourceResolver` | `ACTIVE` |
| `POST` | `/api/push/add` | `StreamPushController#add` | `REQUIRE_PERMISSION` | `video:push:add` | — | `ACTIVE` |
| `DELETE` | `/api/push/batchRemove` | `StreamPushController#batchStop` | `REQUIRE_RESOURCE` | `video:push:remove` | `StreamResourceResolver` | `ACTIVE` |
| `POST` | `/api/push/remove` | `StreamPushController#delete` | `REQUIRE_RESOURCE` | `video:push:remove` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/push/list` | `StreamPushController#list` | `REQUIRE_RESOURCE` | `video:push:list` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/push/start` | `StreamPushController#start` | `REQUIRE_RESOURCE` | `video:push:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/push/forceClose` | `StreamPushController#stop` | `REQUIRE_RESOURCE` | `video:push:stop` | `StreamResourceResolver` | `ACTIVE` |
| `POST` | `/api/push/update` | `StreamPushController#update` | `REQUIRE_RESOURCE` | `video:push:edit` | `StreamResourceResolver` | `ACTIVE` |
| `POST` | `/api/push/upload` | `StreamPushController#uploadChannelFile` | `REQUIRE_PERMISSION` | `video:push:add` | — | `ACTIVE` |
| `GET` | `/api/test/hook/list` | `TestController#all` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/test/redis` | `TestController#redis` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `DELETE` | `/api/alarm/clear` | `AlarmController#clear` | `REQUIRE_RESOURCE` | `video:alarm:clear` | `AlarmResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/alarm/delete` | `AlarmController#delete` | `REQUIRE_RESOURCE` | `video:alarm:remove` | `AlarmResourceResolver` | `ACTIVE` |
| `GET` | `/api/alarm/list` | `AlarmController#list` | `REQUIRE_RESOURCE` | `video:alarm:list` | `AlarmResourceResolver` | `ACTIVE` |
| `GET` | `/api/alarm/snap/{id}` | `AlarmController#snap` | `REQUIRE_RESOURCE` | `video:alarm:list` | `AlarmResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/collect/add` | `CloudRecordController#addCollect` | `REQUIRE_RESOURCE` | `video:cloud-record:collect` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/task/add` | `CloudRecordController#addTask` | `REQUIRE_RESOURCE` | `video:cloud-record:list` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/collect/delete` | `CloudRecordController#deleteCollect` | `REQUIRE_RESOURCE` | `video:cloud-record:collect` | `RecordResourceResolver` | `ACTIVE` |
| `DELETE` | `/api/cloud/record/delete` | `CloudRecordController#deleteFileByIds` | `REQUIRE_RESOURCE` | `video:cloud-record:remove` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/zip` | `CloudRecordController#downloadZipFile` | `REQUIRE_RESOURCE` | `video:cloud-record:download` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/download/zip` | `CloudRecordController#downloadZipFileFromUrl` | `REQUIRE_RESOURCE` | `video:cloud-record:download` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/play/path` | `CloudRecordController#getPlayUrlPath` | `REQUIRE_RESOURCE` | `video:cloud-record:download` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/loadRecord` | `CloudRecordController#loadRecord` | `REQUIRE_RESOURCE` | `video:cloud-record:play` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/date/list` | `CloudRecordController#openRtpServer` | `REQUIRE_RESOURCE` | `video:cloud-record:list` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/list` | `CloudRecordController#openRtpServer` | `REQUIRE_RESOURCE` | `video:cloud-record:list` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/task/list` | `CloudRecordController#queryTaskList` | `REQUIRE_RESOURCE` | `video:cloud-record:list` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/seek` | `CloudRecordController#seekRecord` | `REQUIRE_RESOURCE` | `video:cloud-record:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/cloud/record/speed` | `CloudRecordController#setRecordSpeed` | `REQUIRE_RESOURCE` | `video:cloud-record:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/log/file/{fileName}` | `LogController#downloadFile` | `REQUIRE_PERMISSION` | `operations:log:download` | — | `ACTIVE` |
| `GET` | `/api/log/list` | `LogController#queryList` | `REQUIRE_PERMISSION` | `operations:log:view` | — | `ACTIVE` |
| `POST` | `/api/log/ws-ticket` | `LogWebSocketTicketController#issue` | `REQUIRE_PERMISSION` | `operations:log:view` | — | `ACTIVE` |
| `POST` | `/api/media/play-ticket` | `MediaAccessController#issuePlayTicket` | `AUTHENTICATED` | — | — | `ACTIVE` |
| `GET` | `/api/media/download/{ticket}` | `SecuredDownloadController#download` | `PUBLIC` | — | — | `ACTIVE` |
| `POST` | `/api/media/download-ticket` | `SecuredDownloadController#issue` | `AUTHENTICATED` | — | — | `ACTIVE` |
| `POST` | `/api/media/share` | `StreamShareController#createShare` | `REQUIRE_PERMISSION` | `video:stream:share` | — | `ACTIVE` |
| `POST` | `/api/public/share/{shareId}/play-ticket` | `StreamShareController#exchangeShare` | `PUBLIC` | — | — | `ACTIVE` |
| `DELETE` | `/api/media/share/{shareId}` | `StreamShareController#revokeShare` | `REQUIRE_PERMISSION` | `video:stream:revoke-share` | — | `ACTIVE` |
| `GET` | `/api/ps/receive/close` | `PsController#closeRtpServer` | `REQUIRE_PERMISSION` | `video:ps:manage` | — | `ACTIVE` |
| `GET` | `/api/ps/send/stop` | `PsController#closeSendRTP` | `REQUIRE_RESOURCE` | `video:ps:manage` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/ps/getTestPort` | `PsController#getTestPort` | `REQUIRE_PERMISSION` | `video:ps:manage` | — | `ACTIVE` |
| `GET` | `/api/ps/receive/open` | `PsController#openRtpServer` | `REQUIRE_PERMISSION` | `video:ps:manage` | — | `ACTIVE` |
| `GET` | `/api/ps/send/start` | `PsController#sendRTP` | `REQUIRE_RESOURCE` | `video:ps:manage` | `StreamResourceResolver` | `ACTIVE` |
| `POST` | `/api/record/plan/add` | `RecordPlanController#add` | `REQUIRE_PERMISSION` | `video:cloud-record:collect` | — | `ACTIVE` |
| `DELETE` | `/api/record/plan/delete` | `RecordPlanController#delete` | `REQUIRE_RESOURCE` | `video:cloud-record:remove` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/record/plan/get` | `RecordPlanController#get` | `REQUIRE_RESOURCE` | `video:cloud-record:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/record/plan/link` | `RecordPlanController#link` | `REQUIRE_RESOURCE` | `video:cloud-record:collect` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/record/plan/query` | `RecordPlanController#query` | `REQUIRE_PERMISSION` | `video:cloud-record:list` | — | `ACTIVE` |
| `GET` | `/api/record/plan/channel/list` | `RecordPlanController#queryChannelList` | `REQUIRE_RESOURCE` | `video:cloud-record:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/record/plan/update` | `RecordPlanController#update` | `REQUIRE_RESOURCE` | `video:cloud-record:collect` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/rtp/receive/close` | `RtpController#closeRtpServer` | `REQUIRE_PERMISSION` | `video:rtp:manage` | — | `ACTIVE` |
| `GET` | `/api/rtp/send/stop` | `RtpController#closeSendRTP` | `REQUIRE_RESOURCE` | `video:rtp:manage` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/rtp/receive/open` | `RtpController#openRtpServer` | `REQUIRE_PERMISSION` | `video:rtp:manage` | — | `ACTIVE` |
| `GET` | `/api/rtp/send/start` | `RtpController#sendRTP` | `REQUIRE_RESOURCE` | `video:rtp:manage` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/server/version` | `ServerController#VersionPogetVersion` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/media_server/record/check` | `ServerController#checkMediaRecordServer` | `REQUIRE_PERMISSION` | `video:media-server:manage` | — | `ACTIVE` |
| `GET` | `/api/server/media_server/check` | `ServerController#checkMediaServer` | `REQUIRE_PERMISSION` | `video:media-server:manage` | — | `ACTIVE` |
| `DELETE` | `/api/server/media_server/delete` | `ServerController#deleteMediaServer` | `REQUIRE_PERMISSION` | `video:media-server:manage` | — | `ACTIVE` |
| `GET` | `/api/server/system/configInfo` | `ServerController#getConfigInfo` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/info` | `ServerController#getInfo` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/map/config` | `ServerController#getMapConfig` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/map/model-icon/list` | `ServerController#getMapModelIconList` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/media_server/media_info` | `ServerController#getMediaInfo` | `REQUIRE_RESOURCE` | `video:media-server:list` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/server/media_server/load` | `ServerController#getMediaLoad` | `REQUIRE_PERMISSION` | `video:media-server:list` | — | `ACTIVE` |
| `GET` | `/api/server/media_server/one/{id}` | `ServerController#getMediaServer` | `REQUIRE_PERMISSION` | `video:media-server:list` | — | `ACTIVE` |
| `GET` | `/api/server/media_server/list` | `ServerController#getMediaServerList` | `REQUIRE_PERMISSION` | `video:media-server:list` | — | `ACTIVE` |
| `GET` | `/api/server/media_server/online/list` | `ServerController#getOnlineMediaServerList` | `REQUIRE_PERMISSION` | `video:media-server:list` | — | `ACTIVE` |
| `GET` | `/api/server/resource/info` | `ServerController#getResourceInfo` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/system/info` | `ServerController#getSystemInfo` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `GET` | `/api/server/config` | `ServerController#getVersion` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `ACTIVE` |
| `POST` | `/api/server/media_server/hook-secret/rotate` | `ServerController#rotateMediaHookSecret` | `REQUIRE_PERMISSION` | `video:media-server:manage` | — | `ACTIVE` |
| `POST` | `/api/server/media_server/save` | `ServerController#saveMediaServer` | `REQUIRE_PERMISSION` | `video:media-server:manage` | — | `ACTIVE` |
| `GET` | `/api/server/shutdown` | `ServerController#shutdown` | `REQUIRE_PERMISSION` | `video:media-server:manage` | — | `ACTIVE` |
| `GET` | `/api/system/operation-log` | `SystemOperationLogController#query` | `REQUIRE_PERMISSION` | `operations:log:view` | — | `ACTIVE` |
| `POST` | `/api/role` | `RoleController#add` | `REQUIRE_PERMISSION` | `system:role:add` | — | `DEPRECATED` |
| `POST` | `/api/role/add` | `RoleController#addLegacy` | `REQUIRE_PERMISSION` | `system:role:add` | — | `DEPRECATED` |
| `GET` | `/api/role/all` | `RoleController#all` | `REQUIRE_PERMISSION` | `system:role:list` | — | `DEPRECATED` |
| `DELETE` | `/api/role/delete` | `RoleController#delete` | `REQUIRE_PERMISSION` | `system:role:remove` | — | `DEPRECATED` |
| `PUT` | `/api/role` | `RoleController#update` | `REQUIRE_PERMISSION` | `system:role:edit` | — | `DEPRECATED` |
| `POST` | `/api/userApiKey/add` | `UserApiKeyController#addLegacy` | `REQUIRE_PERMISSION` | `system:apikey:add` | — | `ACTIVE` |
| `POST` | `/api/userApiKey` | `UserApiKeyController#create` | `REQUIRE_PERMISSION` | `system:apikey:add` | — | `ACTIVE` |
| `DELETE` | `/api/userApiKey/delete` | `UserApiKeyController#delete` | `REQUIRE_PERMISSION` | `system:apikey:remove` | — | `ACTIVE` |
| `POST` | `/api/userApiKey/disable` | `UserApiKeyController#disable` | `REQUIRE_PERMISSION` | `system:apikey:disable` | — | `ACTIVE` |
| `POST` | `/api/userApiKey/enable` | `UserApiKeyController#enable` | `REQUIRE_PERMISSION` | `system:apikey:disable` | — | `ACTIVE` |
| `GET` | `/api/userApiKey/userApiKeys` | `UserApiKeyController#page` | `REQUIRE_PERMISSION` | `system:apikey:list` | — | `ACTIVE` |
| `GET` | `/api/userApiKey/scope-options` | `UserApiKeyController#scopeOptions` | `REQUIRE_PERMISSION` | `system:apikey:list` | — | `ACTIVE` |
| `POST` | `/api/userApiKey/remark` | `UserApiKeyController#remark` | `REQUIRE_PERMISSION` | `system:apikey:rotate` | — | `ACTIVE` |
| `POST` | `/api/userApiKey/reset` | `UserApiKeyController#rotate` | `REQUIRE_PERMISSION` | `system:apikey:rotate` | — | `ACTIVE` |
| `PUT` | `/api/userApiKey/{id}/scope` | `UserApiKeyController#updateScope` | `REQUIRE_PERMISSION` | `system:apikey:rotate` | — | `ACTIVE` |
| `POST` | `/api/user/add` | `UserController#addLegacy` | `REQUIRE_PERMISSION` | `system:user:add` | — | `DEPRECATED` |
| `GET` | `/api/user/all` | `UserController#all` | `REQUIRE_PERMISSION` | `system:user:list` | — | `DEPRECATED` |
| `PUT` | `/api/user/{userId}/roles` | `UserController#assignRoles` | `REQUIRE_PERMISSION` | `system:role:assign` | — | `DEPRECATED` |
| `POST` | `/api/user/changePassword` | `UserController#changePassword` | `AUTHENTICATED` | — | — | `DEPRECATED` |
| `POST` | `/api/user/changePushKey` | `UserController#changePushKey` | `REQUIRE_PERMISSION` | `system:user:edit` | — | `DEPRECATED` |
| `POST` | `/api/user` | `UserController#create` | `REQUIRE_PERMISSION` | `system:user:add` | — | `DEPRECATED` |
| `DELETE` | `/api/user/delete` | `UserController#delete` | `REQUIRE_PERMISSION` | `system:user:remove` | — | `DEPRECATED` |
| `DELETE` | `/api/user/{id}` | `UserController#deleteByPath` | `REQUIRE_PERMISSION` | `system:user:remove` | — | `DEPRECATED` |
| `ANY` | `/api/user/userInfo` | `UserController#getUserInfo` | `AUTHENTICATED` | — | — | `DEPRECATED` |
| `POST` | `/api/user/login` | `UserController#legacyLogin` | `PUBLIC` | — | — | `DEPRECATED` |
| `POST` | `/api/user/changePasswordForAdmin` | `UserController#resetPassword` | `REQUIRE_PERMISSION` | `system:user:resetPwd` | — | `DEPRECATED` |
| `GET` | `/api/user/users` | `UserController#users` | `REQUIRE_PERMISSION` | `system:user:list` | — | `DEPRECATED` |
| `GET` | `/api/sy/record/collect/add` | `CameraChannelController#addCollect` | `REQUIRE_RESOURCE` | `video:cloud-record:collect` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/record/collect/delete` | `CameraChannelController#deleteCollect` | `REQUIRE_RESOURCE` | `video:cloud-record:collect` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/record/zip` | `CameraChannelController#downloadZipFile` | `REQUIRE_RESOURCE` | `video:cloud-record:download` | `RecordResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/one` | `CameraChannelController#getOne` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/push/play` | `CameraChannelController#getStreamInfoByAppAndStream` | `REQUIRE_RESOURCE` | `video:push:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/push/play-without-check` | `CameraChannelController#getStreamInfoByAppAndStreamWithoutCheck` | `REQUIRE_RESOURCE` | `video:push:play` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/control/play` | `CameraChannelController#play` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/control/ptz` | `CameraChannelController#ptz` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/cont-with-child` | `CameraChannelController#queryCountWithChild` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/list` | `CameraChannelController#queryList` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/list/address` | `CameraChannelController#queryListByAddressAndDirectionType` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/sy/camera/list/ids` | `CameraChannelController#queryListByDeviceIds` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/list-for-mobile` | `CameraChannelController#queryListForMobile` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/list/box` | `CameraChannelController#queryListInBox` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/list/circle` | `CameraChannelController#queryListInCircle` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `POST` | `/api/sy/camera/list/polygon` | `CameraChannelController#queryListInPolygon` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/list-with-child` | `CameraChannelController#queryListWithChild` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/meeting/list` | `CameraChannelController#queryMeetingChannelList` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/forceClose` | `CameraChannelController#stop` | `REQUIRE_RESOURCE` | `video:push:stop` | `StreamResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/control/stop` | `CameraChannelController#stopPlay` | `REQUIRE_RESOURCE` | `video:channel:stop` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/test` | `CameraChannelController#test` | `REQUIRE_RESOURCE` | `video:channel:list` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/sy/camera/update` | `CameraChannelController#updateCamera` | `REQUIRE_RESOURCE` | `video:channel:edit` | `ChannelResourceResolver` | `ACTIVE` |
| `GET` | `/api/v1/control/preset` | `ApiControlController#list` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/control/ptz` | `ApiControlController#ptz` | `REQUIRE_RESOURCE` | `video:ptz:control` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/getserverinfo` | `ApiController#getserverinfo` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `DEPRECATED` |
| `GET` | `/api/v1/userinfo` | `ApiController#userinfo` | `REQUIRE_PERMISSION` | `operations:system:view` | — | `DEPRECATED` |
| `GET` | `/api/v1/device/channellist` | `ApiDeviceController#channellist` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/device/fetchpreset` | `ApiDeviceController#list` | `REQUIRE_RESOURCE` | `video:ptz:preset` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/device/list` | `ApiDeviceController#list` | `REQUIRE_RESOURCE` | `video:device:list` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/stream/start` | `ApiStreamController#start` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/stream/stop` | `ApiStreamController#stop` | `REQUIRE_RESOURCE` | `video:channel:stop` | `ChannelResourceResolver` | `DEPRECATED` |
| `GET` | `/api/v1/stream/touch` | `ApiStreamController#touch` | `REQUIRE_RESOURCE` | `video:channel:play` | `ChannelResourceResolver` | `DEPRECATED` |

## 维护规则

1. 新增或修改控制器映射时，必须同时更新源码授权标记和 CSV；测试必须证明两者与实际 Spring MVC 映射完全一致。
2. `PUBLIC`、`INTERNAL`、`MEDIA_HOOK` 必须使用显式注解并填写非空 reason。
3. `REQUIRE_PERMISSION` 与 `REQUIRE_RESOURCE` 的权限码必须存在于 2.8.0 数据库种子中。
4. `REQUIRE_RESOURCE` 只能使用已注册解析器，不能回退到 `LEGACY_AUDIT`；列表、对象和批量策略必须同步到视频资源授权矩阵。
5. `MEDIA_HOOK` 必须保持在独立媒体节点认证链；不得合并进普通用户 `permitAll`，也不得接受 JWT 或 API Key 代替节点凭证。
