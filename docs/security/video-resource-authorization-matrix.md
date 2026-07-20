# 视频资源授权矩阵

> 机器事实源是 `src/test/resources/security/controller-permission-matrix.csv`。本表只列 `REQUIRE_RESOURCE` 端点，并由 `VideoAuthorizationMatrixTest` 校验 Method/Path/Permission/Resolver 与 CSV 完全一致。当前共 283 个资源端点。

## 判定口径

- 解析器只允许代码中真实注册的 `ChannelResourceResolver`、`StreamResourceResolver`、`RecordResourceResolver`、`AlarmResourceResolver`。
- 查询、计数、统计和导出在 SQL 边界消费与权限码绑定的 `VideoScopeQuery`；终端内查询先授权目标对象；内存会话列表逐项过滤。
- 详情和派生对象读取优先以 404 隐藏越权/不存在对象；控制动作在任何 SIP、媒体、Redis 写入或数据库变更前以 403 拒绝。
- 批量操作先解析并授权全部成员，任一成员无权时整批失败，不允许“只处理有权子集”。
- `LEGACY_UNBOUND` 录像仅系统用户可见；普通用户即便拥有 ALL 视频范围也不会获得不可追溯录像。
- 媒体 Hook 已由独立节点认证链保护；它不是用户视频资源端点，因此不列入本表。

## enforce 前置条件

2026-07-16 仓库级矩阵、readiness、全量后端和前端门禁已通过。以下条件仍是生产切换 `enforce` 前必须现场复核的运行数据门禁，仓库测试不能代替。

- 本表 283 个资源端点必须与 CSV 完全一致，且全部为 `ACTIVE` 或经批准的 `DEPRECATED` 接口；任何 `LEGACY_AUDIT` 都会阻断 readiness。
- 云录像必须全部绑定 `source_type`、`source_id` 和 `channel_id`。`LEGACY_UNBOUND` 是 expand 期间的隔离状态，不允许带入 enforce。
- 用户、API Key、公开分享三类入口最终都转换为同一资源类型和 ID 进行判断；API Key scope 与分享 scope 只能缩小用户范围。
- 播放和下载响应只返回短期 ticket 或相对消费地址，不返回长期可复用媒体 URL；所有协议地址由票据工厂统一生成。
- ZLM/ABL 收到播放请求时，必须先通过节点身份认证，再校验 ticket 中的资源、权限版本、协议、app/stream 和过期时间；异常与未知值拒绝。
- 列表查询必须在 SQL 边界应用 `VideoScopeQuery`，对象与批量操作必须在任何媒体、SIP、Redis 或数据库副作用前完成授权。

生产数据阈值、完整业务周期和回滚方法见 [2.8.0 授权灰度操作手册](../operations/2.8.0-authorization-rollout.md)。

## 完整矩阵

| Method | Path | Permission | Resolver | DenialStatus | ListScope | BatchPolicy |
|---|---|---|---|---|---|---|
| `POST` | `/api/common/channel/group/add` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/group/device/add` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/region/add` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/region/device/add` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/civilCode/unusual/clear` | `video:channel:edit` | `ChannelResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | VideoScopeQuery（SQL 边界） | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `POST` | `/api/common/channel/parent/unusual/clear` | `video:channel:edit` | `ChannelResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | VideoScopeQuery（SQL 边界） | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `GET` | `/api/common/channel/map/thin/clear` | `video:channel:edit` | `ChannelResourceResolver` | 200（越权数据不返回） | 内存结果逐项对象过滤/任务归属校验 | 不适用 |
| `POST` | `/api/common/channel/group/delete` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | VideoScopeQuery（SQL 边界） | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/group/device/delete` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/region/delete` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | VideoScopeQuery（SQL 边界） | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/region/device/delete` | `video:channel:edit` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/common/channel/map/thin/draw` | `video:channel:edit` | `ChannelResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | VideoScopeQuery（SQL 边界） | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `GET` | `/api/common/channel/one` | `video:channel:list` | `ChannelResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/map/thin/tile/{z}/{x}/{y}` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/map/tile/{z}/{x}/{y}` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/playback/pause` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/play` | `video:channel:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/playback` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/civilcode/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/civilCode/unusual/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/parent/unusual/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/parent/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/map/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/common/channel/playback/query` | `video:record:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/common/channel/reset` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/common/channel/map/reset-level` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | VideoScopeQuery（必须 ALL） | 不适用 |
| `GET` | `/api/common/channel/playback/resume` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/map/thin/save` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | VideoScopeQuery（必须 ALL） | 不适用 |
| `GET` | `/api/common/channel/playback/seek` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/playback/speed` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/broadcast/start` | `video:channel:broadcast` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/talk/start` | `video:channel:talk` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/broadcast/stop` | `video:channel:broadcast` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/play/stop` | `video:channel:stop` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/playback/stop` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/talk/stop` | `video:channel:talk` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/map/thin/progress` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | 内存结果逐项对象过滤/任务归属校验 | 不适用 |
| `POST` | `/api/common/channel/update` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/preset/add` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/tour/point/add` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/auxiliary` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/preset/call` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/tour/point/delete` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/preset/delete` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/drag_zoom_in` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/drag_zoom_out` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/fi/focus` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/home_position` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/fi/iris` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/ptz` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/preset/query` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/tour/speed` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/tour/time` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/scan/set/left` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/scan/set/right` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/scan/set/speed` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/tour/start` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/scan/start` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/tour/stop` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/scan/stop` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/common/channel/front-end/wiper` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/config/set/basicParam` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/config/query/basicParam` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/config/query/svacDecodeConfig` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/config/query/svacEncodeConfig` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/config/query/videoParamOpt` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/config/set/videoParamOpt` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/drag_zoom/zoom_in` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/drag_zoom/zoom_out` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/guard` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/home_position` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/i_frame` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/record` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/reset_alarm` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/control/teleboot/{deviceId}` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/alarm` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/device/query/channel/audio` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/devices/{deviceId}/channels` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `DELETE` | `/api/device/query/devices/{deviceId}/delete` | `video:device:remove` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/info` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/devices/{deviceId}/status` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/devices` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/device/query/devices/{deviceId}` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/device/query/devices/{deviceId}/sync` | `video:device:sync` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/channel/one` | `video:device:list` | `ChannelResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/device/query/statistics/keepalive` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `GET` | `/api/device/query/channel/raw` | `video:device:list` | `ChannelResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/device/query/statistics/register` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `GET` | `/api/device/query/snap/{deviceId}/{channelId}` | `video:device:list` | `ChannelResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/device/query/sync_status` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/{deviceId}/sync_status` | `video:device:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/streams` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/device/query/sub_channels/{deviceId}/{channelId}/channels` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/device/query/subscribe/alarm` | `video:device:config` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/subscribe/catalog` | `video:device:sync` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/device/query/subscribe/mobile-position` | `video:position:subscribe` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/device/query/channel/stream/identification/update/` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/device/query/device/update` | `video:device:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/device/query/transport/{deviceId}/{streamMode}` | `video:device:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/gb_record/download/start/{deviceId}/{channelId}` | `video:record:download` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/gb_record/download/stop/{deviceId}/{channelId}/{stream}` | `video:record:download` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/gb_record/download/progress/{deviceId}/{channelId}/{stream}` | `video:record:download` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/gb_record/query/{deviceId}/{channelId}` | `video:record:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/media/getPlayUrl` | `video:channel:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/media/stream_info_by_app_and_stream` | `video:channel:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/position/latest` | `video:position:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/position/subscribe/{deviceId}` | `video:position:subscribe` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/position/history/{deviceId}` | `video:position:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/position/realtime/{deviceId}` | `video:position:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/platform/channel/add` | `video:platform:share-channel` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/platform/channel/device/add` | `video:platform:share-channel` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `DELETE` | `/api/platform/channel/remove` | `video:platform:share-channel` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/platform/channel/push` | `video:platform:share-channel` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/platform/channel/list` | `video:platform:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `POST` | `/api/platform/channel/device/remove` | `video:platform:share-channel` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/platform/channel/custom/update` | `video:platform:share-channel` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/play/broadcast/{deviceId}/{channelId}` | `video:channel:broadcast` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/play/ssrc` | `video:channel:play` | `ChannelResourceResolver` | 200（越权数据不返回） | 内存结果逐项对象过滤/任务归属校验 | 不适用 |
| `GET` | `/api/play/snap` | `video:channel:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/play/start/{deviceId}/{channelId}` | `video:channel:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/play/stop/{deviceId}/{channelId}` | `video:channel:stop` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/play/broadcast/stop/{deviceId}/{channelId}` | `video:channel:broadcast` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/playback/resume/{streamId}` | `video:record:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/playback/speed/{streamId}/{speed}` | `video:record:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/playback/stop/{deviceId}/{channelId}/{stream}` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/playback/pause/{streamId}` | `video:record:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/playback/seek/{streamId}/{seekTime}` | `video:record:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/playback/start/{deviceId}/{channelId}` | `video:record:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/cruise/point/add/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/preset/add/{deviceId}/{channelId}` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/auxiliary/{deviceId}/{channelId}` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/preset/call/{deviceId}/{channelId}` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/cruise/point/delete/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/preset/delete/{deviceId}/{channelId}` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/fi/focus/{deviceId}/{channelId}` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/common/{deviceId}/{channelId}` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/fi/iris/{deviceId}/{channelId}` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/ptz/{deviceId}/{channelId}` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/preset/query/{deviceId}/{channelId}` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/cruise/speed/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/cruise/time/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/scan/set/left/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/scan/set/right/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/scan/set/speed/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/cruise/start/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/scan/start/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/cruise/stop/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/scan/stop/{deviceId}/{channelId}` | `video:ptz:cruise` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/front-end/wiper/{deviceId}/{channelId}` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/area/circle/add` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/area/rectangle/add` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/attribute` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/live/switch` | `video:jt:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/config/get` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/confirmation-alarm-message` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/control/connection` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/live/continue` | `video:jt:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/control/door` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/area/circle/delete` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/area/polygon/delete` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/area/rectangle/delete` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/media/upload/one/delete` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/route/delete` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/area/circle/edit` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/area/rectangle/edit` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/control/factory-reset` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/fill-light` | `video:jt:ptz` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/playback/downloadUrl` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/link-detection` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/live/pause` | `video:jt:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/record/list` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `GET` | `/api/jt1078/ptz` | `video:jt:ptz` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/area/circle/query` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/area/polygon/query` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/area/rectangle/query` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/driver-information` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `GET` | `/api/jt1078/media/attribute` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `POST` | `/api/jt1078/media/list` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `GET` | `/api/jt1078/position-info` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 不适用 |
| `GET` | `/api/jt1078/route/query` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/playback/control` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/playback/start` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/control/reset` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/area/polygon/set` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/config/set` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/set-phone-book` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/route/set` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/shooting` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/snap` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/live/start` | `video:jt:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/record/start` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/talk/start` | `video:jt:talk` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/live/stop` | `video:jt:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/playback/stop` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/record/stop` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/talk/stop` | `video:jt:talk` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/telephone-callback` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/control/temp-position-tracking` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/text-msg` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/area/circle/update` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `POST` | `/api/jt1078/area/rectangle/update` | `video:jt:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 先授权目标终端，再执行终端内 ID 集合操作 |
| `GET` | `/api/jt1078/media/upload/one/upload` | `video:jt:record` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/wiper` | `video:jt:ptz` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/terminal/channel/add` | `video:jt:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/jt1078/terminal/delete` | `video:jt:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/jt1078/terminal/channel/delete` | `video:jt:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/terminal/channel/one` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/terminal/channel/list` | `video:jt:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/jt1078/terminal/query` | `video:jt:list` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/jt1078/terminal/list` | `video:jt:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `POST` | `/api/jt1078/terminal/channel/update` | `video:jt:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/jt1078/terminal/update` | `video:jt:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/proxy/del` | `video:proxy:remove` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/proxy/delete` | `video:proxy:remove` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/proxy/list` | `video:proxy:list` | `StreamResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/proxy/one` | `video:proxy:list` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/proxy/start` | `video:proxy:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/proxy/stop` | `video:proxy:stop` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/proxy/update` | `video:proxy:edit` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/push/batchRemove` | `video:push:remove` | `StreamResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/push/remove` | `video:push:remove` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/push/list` | `video:push:list` | `StreamResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/push/start` | `video:push:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/push/forceClose` | `video:push:stop` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `POST` | `/api/push/update` | `video:push:edit` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/alarm/clear` | `video:alarm:clear` | `AlarmResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | VideoScopeQuery（SQL 边界） | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `DELETE` | `/api/alarm/delete` | `video:alarm:remove` | `AlarmResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/alarm/list` | `video:alarm:list` | `AlarmResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/alarm/snap/{id}` | `video:alarm:list` | `AlarmResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/cloud/record/collect/add` | `video:cloud-record:collect` | `RecordResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | Record ID 对象门禁；筛选模式使用 VideoScopeQuery | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `GET` | `/api/cloud/record/task/add` | `video:cloud-record:list` | `RecordResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | VideoScopeQuery（SQL 边界） | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `GET` | `/api/cloud/record/collect/delete` | `video:cloud-record:collect` | `RecordResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | Record ID 对象门禁；筛选模式使用 VideoScopeQuery | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `DELETE` | `/api/cloud/record/delete` | `video:cloud-record:remove` | `RecordResourceResolver` | 403（整批拒绝；不存在对象为 404） | 不适用 | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/cloud/record/zip` | `video:cloud-record:download` | `RecordResourceResolver` | 403（整批拒绝；不存在对象为 404） | VideoScopeQuery（SQL 边界） | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/cloud/record/download/zip` | `video:cloud-record:download` | `RecordResourceResolver` | 403（整批拒绝；不存在对象为 404） | VideoScopeQuery（SQL 边界） | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/cloud/record/play/path` | `video:cloud-record:download` | `RecordResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/cloud/record/loadRecord` | `video:cloud-record:play` | `RecordResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/cloud/record/date/list` | `video:cloud-record:list` | `RecordResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/cloud/record/list` | `video:cloud-record:list` | `RecordResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/cloud/record/task/list` | `video:cloud-record:list` | `RecordResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（逐任务回查录像来源） | 不适用 |
| `GET` | `/api/cloud/record/seek` | `video:cloud-record:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/cloud/record/speed` | `video:cloud-record:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/ps/send/stop` | `video:ps:manage` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/ps/send/start` | `video:ps:manage` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `DELETE` | `/api/record/plan/delete` | `video:cloud-record:remove` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/record/plan/get` | `video:cloud-record:list` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 全量预授权；任一成员拒绝则整批不执行 |
| `POST` | `/api/record/plan/link` | `video:cloud-record:collect` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | VideoScopeQuery（SQL 边界） | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/record/plan/channel/list` | `video:cloud-record:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `POST` | `/api/record/plan/update` | `video:cloud-record:collect` | `ChannelResourceResolver` | 403（整批拒绝；不存在对象为 404） | 目标对象或关联通道对象授权 | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/rtp/send/stop` | `video:rtp:manage` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/rtp/send/start` | `video:rtp:manage` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/server/media_server/media_info` | `video:media-server:list` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/record/collect/add` | `video:cloud-record:collect` | `RecordResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | Record ID 对象门禁；筛选模式使用 VideoScopeQuery | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `GET` | `/api/sy/record/collect/delete` | `video:cloud-record:collect` | `RecordResourceResolver` | 403（拒绝越权筛选；无可见数据不执行） | Record ID 对象门禁；筛选模式使用 VideoScopeQuery | 仅对有效 VideoScopeQuery 范围执行，不做越权部分成功 |
| `GET` | `/api/sy/record/zip` | `video:cloud-record:download` | `RecordResourceResolver` | 403（整批拒绝；不存在对象为 404） | VideoScopeQuery（SQL 边界） | 全量预授权；任一成员拒绝则整批不执行 |
| `GET` | `/api/sy/camera/one` | `video:channel:list` | `ChannelResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/sy/push/play` | `video:push:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/push/play-without-check` | `video:push:play` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/camera/control/play` | `video:channel:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/camera/control/ptz` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/camera/cont-with-child` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/list/address` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `POST` | `/api/sy/camera/list/ids` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/list-for-mobile` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/list/box` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/list/circle` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `POST` | `/api/sy/camera/list/polygon` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/list-with-child` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/camera/meeting/list` | `video:channel:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/sy/forceClose` | `video:push:stop` | `StreamResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/camera/control/stop` | `video:channel:stop` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/sy/test` | `video:channel:list` | `ChannelResourceResolver` | 404（隐藏越权或不存在对象） | 不适用 | 不适用 |
| `GET` | `/api/sy/camera/update` | `video:channel:edit` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/v1/control/preset` | `video:ptz:preset` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/v1/control/ptz` | `video:ptz:control` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/v1/device/channellist` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/v1/device/fetchpreset` | `video:ptz:preset` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/v1/device/list` | `video:device:list` | `ChannelResourceResolver` | 200（越权数据不返回） | VideoScopeQuery（SQL 边界） | 不适用 |
| `GET` | `/api/v1/stream/start` | `video:channel:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/v1/stream/stop` | `video:channel:stop` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
| `GET` | `/api/v1/stream/touch` | `video:channel:play` | `ChannelResourceResolver` | 403（副作用前拒绝；不存在对象为 404） | 不适用 | 不适用 |
