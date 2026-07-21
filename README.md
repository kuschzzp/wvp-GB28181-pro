![logo](doc/_media/logo.png)
# 开箱即用的国标28181和部标808+1078协议视频平台

[![Build Status](https://travis-ci.org/xia-chu/ZLMediaKit.svg?branch=master)](https://travis-ci.org/xia-chu/ZLMediaKit)
[![license](http://img.shields.io/badge/license-MIT-green.svg)](https://github.com/xia-chu/ZLMediaKit/blob/master/LICENSE)
[![JAVA](https://img.shields.io/badge/language-java-red.svg)](https://en.cppreference.com/)
[![platform](https://img.shields.io/badge/platform-linux%20|%20macos%20|%20windows-blue.svg)](https://github.com/xia-chu/ZLMediaKit)
[![PRs Welcome](https://img.shields.io/badge/PRs-welcome-yellow.svg)](https://github.com/xia-chu/ZLMediaKit/pulls)


WEB VIDEO PLATFORM是一个基于GB28181-2016、部标808、部标1078标准实现的开箱即用的网络视频平台，负责实现核心信令与设备管理后台部分，支持NAT穿透，支持海康、大华、宇视等品牌的IPC、NVR接入。支持国标级联，支持将不带国标功能的摄像机/直播流/直播推流转发到其他国标平台。

流媒体服务基于@夏楚 ZLMediaKit [https://github.com/ZLMediaKit/ZLMediaKit](https://github.com/ZLMediaKit/ZLMediaKit)   
播放器使用@dexter jessibuca [https://github.com/langhuihui/jessibuca/tree/v3](https://github.com/langhuihui/jessibuca/tree/v3)  
播放器使用@Numberwolf-Yanlong h265web.js [https://github.com/numberwolf/h265web.js](https://github.com/numberwolf/h265web.js)  
前端页面基于vue-admin-template构建 [https://github.com/PanJiaChen/vue-admin-template?tab=readme-ov-file](https://github.com/PanJiaChen/vue-admin-template?tab=readme-ov-file)  

# 应用场景：
- 支持浏览器无插件播放摄像头视频。
- 支持国标设备(摄像机、平台、NVR等)设备接入
- 支持rtsp, rtmp，直播设备设备接入，充分利旧。
- 支持国标级联。多平台级联。跨网视频预览。
- 支持跨网网闸平台互联。


# 文档
wvp使用文档 [https://doc.wvp-pro.cn](https://doc.wvp-pro.cn)  
ZLM使用文档 [https://github.com/ZLMediaKit/ZLMediaKit](https://github.com/ZLMediaKit/ZLMediaKit)

## 身份认证、功能 RBAC 与视频资源授权（2.8.0）

当前版本已接入多角色、菜单/按钮权限、后端动态路由、JWT `tokenVersion` 失效机制和 API Key 安全存储底座。部门只用于人员组织和用户管理数据范围；摄像头、通道、推拉流、录像等资源不挂部门，由独立视频资源组维护资源与角色之间的授权关系。

浏览器认证接口：

- `POST /api/auth/login`：JSON 请求体提交用户名和原始密码，前端不再计算 MD5；旧 MD5 密码在首次成功登录后升级为 BCrypt。
- `GET /api/auth/profile`：返回当前用户、角色键、功能权限和授权版本，不返回密码、pushKey、JWT claims 或 API Key。
- `GET /api/menu/routers`：返回当前用户可见的菜单树；前端只解析固定组件白名单，未知组件直接拒绝。
- `POST /api/auth/logout`：撤销当前 JWT；Redis 不可用时递增用户 `tokenVersion` 作为兜底。

除登录和受控公共入口外，请求通过 `access-token` 请求头携带 JWT。未登录返回 HTTP 401，已登录但权限不足在 enforce 模式返回 HTTP 403。

### 授权模式

生产迁移阶段默认使用：

```yaml
authorization:
  mode: audit
```

- `audit`：计算并写入真实授权结果；对旧业务端点暂时兼容放行，用于上线前收集缺失角色和权限。拒绝记录写入 `wvp_authz_audit`。
- `enforce`：真实阻断无权限请求。只有完成角色配置、端点矩阵清零并观察至少一个完整业务周期后才可切换。
- `disabled`：仅允许 `dev`、`test`、`local` 环境，生产环境启动时会拒绝该配置。

计划 3 的 Task 1～12 已完成自动化阶段验收，用户侧业务验证进行中。Task 12 已覆盖设备/通道、二级统一通道、`/api/sy` 自定义摄像头、平台共享通道、RTP/PS 发流、媒体信息、快照、活动 SSRC、云录像合并任务和 DeviceControl SIP 指令等入口。438 个 HTTP Handler 已全部分类，其中 286 个资源端点只引用代码中真实注册的 4 类解析器，不再包含 `REQUIRE_RESOURCE + LEGACY_AUDIT`；7 个定向测试类共 94 项全部通过。旧 `ChannelProviderTest` 已补充强制 `VideoScopeQuery` 夹具，其自身 17 项测试通过；后端全量覆盖 91 个测试套件、497 项测试，0 失败、0 错误、1 项既有跳过。前端 20 个测试套件、96 项测试全部通过，ESLint 为 0 error（保留 680 条 warning）。

独立视频资源组、角色视频范围和统一授权门面已经接入；设备、GB28181 通道、统一通道、JT1078 终端/通道、推流、拉流、告警、位置、云录像、首页统计与导出均已在数据库层按视频范围过滤。云录像新记录会在同一事务固化 `source_type/source_id/channel_id`；旧录像按 `call_id -> app+stream 当前/历史会话 -> 统一通道映射` 的顺序回填，只有唯一候选才绑定，无法唯一绑定的记录标记为 `LEGACY_UNBOUND` 并保存 `source_reason`，仅 systemUser 可见。HTTP 查询与内部维护查询已显式拆分，搜索、时间范围、分页、统计和导出复用同一授权条件。GB28181 点播、停止、回放控制、PTZ、广播、对讲和国标录像命令、JT1078 控制与媒体操作、推流、拉流以及兼容 API，已在业务副作用前解析内部稳定资源并调用对象授权门面；受保护 MVC Handler 必须为可被 Method Security 代理的 `public` 方法。GB 设备/通道、JT 终端/通道、推流和拉流删除时会在同一业务事务清理授权关系；通道内部 ID 变化时只允许按同类型稳定来源唯一迁移显式授权，零候选或多候选会停止迁移并持久化人工处理审计项，不会扩大为来源设备授权。Task 9 的 17 个组合定向测试类共 75 项测试全部通过，覆盖直接控制器、MockMvc 403/404、Method Security、解析器、稳定 ID、删除回滚与迁移人工项。Task 10 已把 JT1078 `/playback/download` 从客户端任意服务器 `path` 改为服务端受控 UUID `downloadId`，并在输出前按原终端/通道重新授权；同时补齐派生对象、畸形参数、批量全量授权和地图轨迹参数门禁。短期下载票据仍由计划 4 实现。`enforce` 模式按已接入的视频范围过滤，`audit` 模式仍兼容放行并记录审计。升级得到的 `legacy_role_*` 会先获得显式兼容授权，管理员第一次保存角色后由实际菜单配置整体替换；重复执行升级脚本不会把兼容权限重新授回。

ZLMediaKit/ABL Hook 已启用独立媒体节点认证链：每节点使用独立 Hook 密钥、节点类型和可选 CIDR 白名单，敏感回调支持 timestamp/nonce 重放防护；该链与用户 JWT/API Key 完全隔离。Hook 密钥在数据库中只保存 HMAC 哈希，轮换时保留旧密钥最长 5 分钟，管理接口不返回明文。

旧版“构建推流地址”会把长期 `pushKey` 暴露给浏览器并生成可重复使用的 MD5 签名，当前阶段已显式停用；已有外部推流任务不受页面调整影响，新的短期推流凭证随计划 4 的媒体安全改造接入。GB28181/JT1078 内部对讲流按服务端既有规则不使用普通推流 `pushKey`。

### Docker 全新部署

`docker/` 目录已经内置完整的 2.8 MySQL 初始化链，Compose 只挂载英文路径。使用空 MySQL 数据目录启动时，会依次创建业务基础结构、权限结构和最终用户角色关系表，不需要额外执行 `数据库/` 下的升级脚本。

#### 启动前准备

1. 安装 Docker，并确认当前环境支持 `docker compose`。
2. 确认 `docker/volumes/mysql/data` 不包含旧 MySQL 数据；MySQL 只会在空数据目录首次启动时执行初始化 SQL。
3. 根据现场网络修改 `docker/.env` 中的流媒体、SIP 地址和端口，尤其是：

```dotenv
Stream_IP=127.0.0.1
SDP_IP=127.0.0.1
SIP_ShowIP=127.0.0.1
SIP_Port=8160
```

如果要接入真实摄像头或国标设备，请把上面的 IP 改成设备可访问到的宿主机 IP，不要继续使用 `127.0.0.1`。

#### 首次启动

在项目根目录执行：

```bash
cd docker
./prepare-env.sh
docker compose up -d --build
```

`./prepare-env.sh` 会自动补齐 `API_KEY_PEPPER` 和 `MEDIA_HOOK_SECRET_PEPPER` 两个密钥空值，并写回 `docker/.env`；如果用户已经手工填写，则不会覆盖。这两个值需要长期保存，不要提交到仓库，后续随意更换会导致已签发的 API Key 或媒体 Hook 凭证失效。

#### 查看状态和日志

```bash
cd docker
docker compose ps
docker compose logs -f polaris-mysql polaris-wvp
```

#### 重新构建启动

代码或前端资源有变化，需要重新构建镜像时执行：

```bash
cd docker
docker compose up -d --build --force-recreate
```

默认 Web 入口是 `http://宿主机IP:8080`，默认登录账号和密码均为 `admin`。该入口只用于空库新装，不处理已有数据目录升级；更多说明见 [Docker 全新部署说明](docker/README.md)。

### 非 Docker 升级与回滚

1. 备份数据库，执行对应数据库的 `数据库/2.8.0/更新-*-2.8.0.sql` expand 脚本。
2. 为生产环境配置持久化 JWK 文件、至少 32 字节的 `API_KEY_PEPPER` 和独立的 `MEDIA_HOOK_SECRET_PEPPER`；不要把真实密钥写进仓库。
3. 先以 `authorization.mode=audit` 发布，配置用户多角色和菜单权限，持续检查 `wvp_authz_audit` 的 DENY。
4. 验证动态菜单、401/403、旧密码升级和 API Key 后，再灰度切换 `enforce`。
5. enforce 出现业务权限遗漏时，优先回切 `audit`，权限配置和审计数据不会丢失。contract 清理旧表之前仍可回滚旧应用；contract 之后只能恢复数据库快照，不能直接启动 2.7.4。

完整的机器校验清单见 [HTTP 端点权限矩阵](docs/security/endpoint-permission-matrix.md)，设计与阶段边界见 [RBAC、部门与视频资源组设计](docs/superpowers/specs/2026-07-13-wvp-rbac-dept-resource-design.md)。

# gitee仓库
https://gitee.com/pan648540858/wvp-GB28181-pro.git

# 截图
<table>
    <tr>
        <td ><center><img src="doc/_media/1.png" >登录页面 </center></td>
        <td ><center><img src="doc/_media/2.png" >首页</center></td>
    </tr>
    <tr>
        <td ><center><img src="doc/_media/3.png" >分屏播放 </center></td>
        <td ><center><img src="doc/_media/4.png" >国标设备列表</center></td>
    </tr>
    <tr>
        <td ><center><img src="doc/_media/5.png" >行政区划管理 </center></td>
        <td ><center><img src="doc/_media/8.png" >业务分组管理</center></td>
    </tr>
    <tr>
        <td ><center><img src="doc/_media/6.png" >录制计划</center></td>
        <td ><center><img src="doc/_media/7.png" >平台信息</center></td>
    </tr>
</table>

# 功能特性
- [X] 集成web界面
- [X] 兼容性良好
- [X] 跨平台服务，一次编译多端部署， 可以同时用于x86和arm架构
- [X] 接入设备
  - [X] 视频预览
  - [X] 支持主码流子码流切换
  - [X] 无限制接入路数，能接入多少设备只取决于你的服务器性能
  - [X] 云台控制，控制设备转向，拉近，拉远
  - [X] 预置位查询，使用与设置
  - [X] 查询NVR/IPC上的录像与播放，支持指定时间播放与下载
  - [X] 无人观看自动断流，节省流量
  - [X] 视频设备信息同步
  - [X] 离在线监控
  - [X] 支持直接输出RTSP、RTMP、HTTP-FLV、Websocket-FLV、HLS多种协议流地址
  - [X] 支持通过一个流地址直接观看摄像头，无需登录以及调用任何接口
  - [X] 支持UDP和TCP两种国标信令传输模式
  - [X] 支持UDP和TCP两种国标流传输模式
  - [X] 支持检索,通道筛选
  - [X] 支持通道子目录查询
  - [X] 支持过滤音频，防止杂音影响观看
  - [X] 支持国标网络校时
  - [X] 支持播放H264和H265
  - [X] 报警信息处理，支持向前端推送报警信息
  - [X] 语音对讲
  - [X] 支持业务分组和行政区划树自定义展示以及级联推送
  - [X] 支持订阅与通知方法
    - [X] 移动位置订阅
    - [X] 移动位置通知处理
    - [X] 报警事件订阅
    - [X] 报警事件通知处理
    - [X] 设备目录订阅
    - [X] 设备目录通知处理
  -  [X] 移动位置查询和显示
  - [X] 支持手动添加设备和给设备设置单独的密码
-  [X] 支持平台对接接入
-  [X] 支持国标级联
  - [X] 国标通道向上级联
    - [X] WEB添加上级平台
    - [X] 注册
    - [X] 心跳保活
    - [X] 通道选择
    - [X] 支持通道编号自定义, 支持每个平台使用不同的通道编号
    - [X] 通道推送
    - [X] 点播
    - [X] 云台控制
    - [X] 平台状态查询
    - [X] 平台信息查询
    - [X] 平台远程启动
    - [X] 每个级联平台可自定义的虚拟目录
    - [X] 目录订阅与通知
    - [X] 录像查看与播放
    - [X] GPS订阅与通知（直播推流）
    - [X] 语音对讲
  - [X] 支持同时级联到多个上级平台
- [X] 支持自动配置ZLM媒体服务, 减少因配置问题所出现的问题;
- [X] 支持流媒体节点集群，负载均衡。
- [X] 支持启用udp多端口模式, 提高udp模式下媒体传输性能;
- [X] 支持公网部署；
- [X] 支持wvp与zlm分开部署，提升平台并发能力
- [X] 支持拉流RTSP/RTMP，分发为各种流格式，或者推送到其他国标平台
- [X] 支持推流RTSP/RTMP，分发为各种流格式，或者推送到其他国标平台
- [X] 支持推流鉴权
- [X] 支持接口鉴权
- [X] 云端录像，推流/代理/国标视频均可以录制在云端服务器，支持预览和下载
- [X] 支持打包可执行jar和war
- [X] 支持跨域请求，支持前后端分离部署
- [X] 支持Mysql，Postgresql，金仓等数据库
- [X] 支持录制计划, 根据设定的时间对通道进行录制. 暂不支持将录制的内容转发到国标上级
- [X] 支持国标信令集群
- [X] 新增支持部标808和部标1078，大量新特性不一一列表了。支持作为网关被国标上级调用部标设备
- [X] 支持电子地图。支持展示通道位置，支持在地图上修改通道位置。支持了数据分层抽稀数据能力，百万级数据也可以轻松展示。提供标准的矢量瓦片图层，常见地图引擎都可以直接展示。
- [X] 借用zlm闭源版本新能力，可以支持录像保存至s3存储，支持minio。
- [X] **全新虚拟线程支持，极大提升了平台的并发能力，局域网压测轻松接入五万+设备，这不是服务极限，这是我的压测工具和硬件测试服务器的极限，大家可自行测试。生产环境实际性能取决于服务器性能和网络带宽。**
- [X] **支持报警订阅和报警管理，支持报警事件的展示和查询，支持报警时自动获取快照、播放录像。**

# 闭源内容
- [X] 国标增强版: 支持国标28181-2022协议，支持巡航轨迹查询，PTZ精准控制，存储卡格式化，设备软件升级，OSD配置，h265+aac，支持辅码流，录像倒放等。
- [X] 全功能版： 
  - [X] 支持开源所有功能
  - [X] ONVIF协议
    - 自研协议实现，安全可靠。
    - 设备检索
    - 实时图像预览
    - 录像回放、回放倍速控制
    - 云台控制、预置位控制、云台绝对定位、看守位
    - 聚焦控制
    - 设备重启
    - 设备时间设置以及跟系统时间的差值比较
    - 恢复出厂设置
    - 自动获取设备品牌等信息、支持展示DNS信息、支持协议的展示
    - 国标级联点播、自动点播等。
  - [X] **海康私有协议-ISUP5.0/ISUP4.0/ISUP2.0**
    - 设备注册
    - 资源获取
    - 预览
    - 录像查询与回放
    - 云台控制
    - 预置位控制
    - 报警，支持大量报警类型的解析与展示，
      - 绊线检测
      - 区域入侵
      - 移动侦测
      - 逆行检测
      - 徘徊检测
      - 人员聚集
      - 声音异常
      - 设备异常等。
    - 抓图（设备直接上传快照图片到服务器，流量消耗低，无需服务端拉流解码）
    - 对讲支持
    - 设备配置（设备名称、循环录像等配置）
    - 设备信息（设备序列号、类型等）
    - 版本信息（软件、编码、面板、硬件的版本号）
    - 编码配置（主辅码流分辨率、码率、帧率等配置）
    - 图像参数配置（色调、对比度、亮度、饱和度配置）
  - [X] 大华私有协议-设备网络SDK
    - 局域网设备发现
    - 设备主动注册（用于服务器部署在公网时设备的注册）
    - 通道获取
    - 预览
    - 录像回放
    - 录像下载
    - 云台控制，支持预置位控制、巡航组、巡迹、水平旋转、云台速度配置、开机动作、空闲动作、云台限位、定时任务、云台重启
    - 抓图（设备直接上传快照图片到服务器，流量消耗低，无需服务端拉流解码）
    - 喊话（单工）和对讲（双工）
    - 相机配置，包括亮度、对比度、对比度、饱和度、色彩抑制、伽马、锐度、锐度抑制；视角的配置，提供正常、倒影、走廊模式、镜像的配置；曝光、背光、白平衡、日夜模式、数字变倍、聚焦、补光灯、透雾的配置
    - 报警接收
  - [ ] 国标35114协议（开发中...）
  - [X] 国网B接口协议
    - 设备注册
    - 资源获取
    - 预览
    - 云台控制
    - 预置位控制等，
    - 可免费定制支持语音对讲、录像回放和抓拍图像。
  - [X] 支持按权限分配可以使用的通道
  - [X] 支持表格导出
  - [X] 拉流代理支持按照品牌拼接url
  - [X] 播放鉴权，给授权设备无法进行播放，拿到播放地址也不行


# 授权协议
本项目自有代码使用宽松的MIT协议，在保留版权信息的情况下可以自由应用于各自商用、非商业的项目。 但是本项目也零碎的使用了一些其他的开源代码，在商用的情况下请自行替代或剔除； 由于使用本项目而产生的商业纠纷或侵权行为一概与本项目及开发者无关，请自行承担法律风险。 在使用本项目代码时，也应该在授权协议中同时表明本项目依赖的第三方库的协议

# 技术支持

## 官方公众号
<img src="doc/_media/gongzhonghao.jpg" width="40%" height="40%">

> 为大家提供WVP最新的开发进展，未来规划等内容。欢迎关注。

## 付费社群
<img src="doc/_media/shequ.png" width="50%" height="50%">

> 付费社群即可以对作者提供支持，也可以为大家更加快速的解决问题，还为正式加入星球的用户提供了微信群。对星球内容不满意，三天之内退出支持自动退款。如果暂时无法加入，给项目点个星也是极大的鼓励。

[知识星球](https://t.zsxq.com/0d8VAD3Dm)专栏列表：
- [WVP 部署安全加固指南：新手必看，防范攻击与漏洞](https://articles.zsxq.com/id_tv8wz4uubx2n.html)

有偿技术支持，一对一开发辅导，闭源内容合作请发送邮件到648540858@qq.com咨询

# 致谢
感谢作者[夏楚](https://github.com/xia-chu) 提供这么棒的开源流媒体服务框架,并在开发过程中给予支持与帮助。     
感谢作者[dexter langhuihui](https://github.com/langhuihui)和[Numberwolf-Yanlong](https://github.com/numberwolf/h265web.js) 开源这么好用的WEB播放器。      
感谢各位大佬的赞助以及对项目的指正与帮助。包括但不限于代码贡献、问题反馈、资金捐赠等各种方式的支持！以下排名不分先后：  
[lawrencehj](https://github.com/lawrencehj) [Smallwhitepig](https://github.com/Smallwhitepig) [swwhaha](https://github.com/swwheihei)
[hotcoffie](https://github.com/hotcoffie) [xiaomu](https://github.com/nikmu) [TristingChen](https://github.com/TristingChen)
[chenparty](https://github.com/chenparty) [Hotleave](https://github.com/hotleave) [ydwxb](https://github.com/ydwxb)
[ydpd](https://github.com/ydpd) [szy833](https://github.com/szy833) [ydwxb](https://github.com/ydwxb) [Albertzhu666](https://github.com/Albertzhu666)
[mk1990](https://github.com/mk1990) [SaltFish001](https://github.com/SaltFish001)
