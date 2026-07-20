# 09 故障排查

[上一章：日常运维与备份](08-operations-and-backup.md) · [返回首页](README.md) · [下一章：验收清单](10-acceptance-checklist.md)

排错原则：先确定失败阶段，再看证据；每次只改一个变量；从最小影响检查开始。不要一遇到问题就关闭防火墙、关闭 SELinux、删除数据目录或换一堆镜像源。

## 1. 先建立故障时间线

记录：

- 故障发生时间和时区；
- 最后一次正常时间；
- 最近修改的代码、配置、镜像、证书、网络或设备；
- 影响全部设备还是单个品牌/网段/通道；
- 失败属于安装、拉镜像、构建、启动、登录、注册、目录还是播放；
- 是否可以回退，回退点是什么。

先收集只读证据：

```bash
cd /opt/wvp-GB28181-pro/docker
date -Is
docker compose ps
docker compose config --services
docker compose config --images
df -hT
free -h
sudo ss -lntup
docker stats --no-stream
```

再按服务保存最近日志：

```bash
docker compose logs --since=30m polaris-mysql
docker compose logs --since=30m polaris-redis
docker compose logs --since=30m polaris-media
docker compose logs --since=30m polaris-wvp
docker compose logs --since=30m polaris-nginx
```

日志可能包含 IP、设备 ID、URL ticket 或请求参数，分享前脱敏。不要公开 `.env` 或完整 `docker compose config`。

## 2. Docker/Compose 安装失败

### 症状

- `docker: command not found`；
- `docker: 'compose' is not a docker command`；
- dnf/apt 找不到 `docker-ce`；
- Docker daemon 未启动。

### 检查顺序

1. 系统和版本：

   ```bash
   cat /etc/os-release
   uname -m
   ```

2. 仓库是否配置：

   CentOS/Rocky：

   ```bash
   sudo dnf repolist
   grep -E '^(baseurl|metalink|mirrorlist)=' /etc/yum.repos.d/docker-ce.repo
   ```

   Ubuntu：

   ```bash
   cat /etc/apt/sources.list.d/docker.sources
   ls -l /etc/apt/keyrings/docker.asc
   sudo apt update
   ```

3. 软件包：

   ```bash
   docker version
   docker compose version
   rpm -qa | grep -E 'docker|containerd' 2>/dev/null || true
   dpkg -l | grep -E 'docker|containerd' 2>/dev/null || true
   ```

4. 服务：

   ```bash
   sudo systemctl status docker --no-pager
   sudo journalctl -u docker -n 200 --no-pager
   ```

### 判断

- 软件包地址 404：系统代号/版本或仓库路径错误；
- GPG/签名错误：检查时间、密钥和 Signed-By，不要加 `trusted=yes`；
- TUNA/USTC Docker CE 包能装但 `docker pull` 失败：这是正常的故障分层，Docker CE 镜像不是 Docker Hub；
- `docker-compose` 老命令存在但 `docker compose` 不存在：缺 Compose v2 插件，不要混用教程中的 v1 二进制。

## 3. 容器镜像拉取失败

### 症状

`TLS handshake timeout`、`i/o timeout`、`connection reset`、`403 Forbidden`、`manifest unknown`、`toomanyrequests`。

### 检查顺序

```bash
docker info
docker compose config --images
docker pull mysql:8
```

若使用私有仓库：

```bash
REGISTRY_HOST='REPLACE_WITH_YOUR_REGISTRY_HOST'
REGISTRY_PROJECT='REPLACE_WITH_YOUR_REGISTRY_PROJECT'
docker login "$REGISTRY_HOST"
docker pull "$REGISTRY_HOST/$REGISTRY_PROJECT/mysql:8-verified"
```

### 判断

- `403`：账号加速器可能只允许特定云内访问，或凭证/仓库权限不对；
- `manifest unknown`：镜像同步不完整、标签不存在或 CPU 架构不支持；
- `latest` 拉到旧内容：加速器缓存停止更新。阿里 ACR 已明确提示其加速器停止同步最新镜像；不要反复删除再拉；
- 配置了 `docker.mirrors.ustc.edu.cn`：该缓存已关闭，移除并按 [02 服务器准备](02-server-preparation.md) 选择有效方案；
- 拉取限流：登录 Docker Hub 或使用自己的私有仓库，不要循环并发重试。

检查摘要：

```bash
docker image inspect mysql:8 --format '{{json .RepoDigests}}'
docker image inspect zlmediakit/zlmediakit:master --format '{{json .RepoDigests}}'
```

生产排错优先与已验证摘要比较，而不是只比较标签。

## 4. WVP 或前端镜像构建失败

先用纯文本进度重现一遍对应构建：

```bash
docker compose build --progress=plain polaris-wvp
docker compose build --progress=plain polaris-nginx
```

只执行失败的那个，不要每次全量构建。

### 4.1 失败在 `FROM`

属于基础镜像拉取。检查 `ringcentral/jdk:21.0.9`、`ubuntu:24.04`、`nginx:alpine` 的仓库、标签和架构，按上一节处理。

### 4.2 失败在 `apt-get update/install`

属于容器内部 Debian/Ubuntu 软件源，与宿主机 dnf/apt 和 Docker Hub 加速器不同。

从构建日志确认：

- WVP 构建基础镜像实际是 Debian 还是 Ubuntu；
- 源配置文件是 `/etc/apt/sources.list` 还是 DEB822 `*.sources`；
- 失败域名是 `deb.debian.org`、Ubuntu archive/security 还是国内镜像；
- CPU 架构是否需要 Ubuntu ports。

按 TUNA/阿里对应发行版官方镜像帮助修改 Dockerfile，并保留安全更新源及时性。不要对一个不确定发行版盲目执行字符串替换。

### 4.3 失败在 Maven

典型关键字：`Could not resolve dependencies`、`Could not transfer artifact`、插件解析失败、编译失败。

判断：

- URL 是 `maven.aliyun.com`：阿里 Maven 公共仓库/网络问题；
- URL 是 `maven.ecc.no`：项目显式独立仓库问题；
- `Could not find artifact`：镜像中可能确实没有该依赖，不能把网络超时和不存在混为一谈；
- `COMPILATION ERROR`：网络阶段已过去，检查源码、JDK 21、生成代码和具体编译错误；
- `No space left on device`：磁盘或 inode 满。

当前 `pom.xml` 已含阿里公共仓库。不要再往 POM 堆多个相同 `mirrorOf`，Maven 并不会把多个镜像当自动负载均衡。

### 4.4 失败在 npm

当前 Nginx Dockerfile显式使用 `https://registry.npmmirror.com`。检查报错是 registry 包下载、包版本、Node 版本、前端编译还是内存不足。

```bash
df -hT
free -h
```

`npm ERR! code EAI_AGAIN` 多为 DNS/网络；`ERESOLVE` 或源码编译错误不应通过不停换镜像来解决。

### 4.5 构建上下文很慢

```bash
cd /opt/wvp-GB28181-pro
du -sh . web/node_modules target docker/logs docker/volumes 2>/dev/null
```

检查是否上传了本地依赖、录像、日志或构建产物。不要在不确认归属时删除；用干净发布目录重新部署更安全。

## 5. `docker compose config` 失败

### 必填 pepper 为空

报错包含 `API_KEY_PEPPER` 或 `MEDIA_HOOK_SECRET_PEPPER` 时，生成两个不同的 32 字节随机值并填入 `.env`。不要删除 `${VAR:?}` 安全检查。

### YAML 解析错误

```bash
docker compose config --quiet
```

检查最近编辑位置的缩进、冒号、引号和重复键。YAML 用空格，不能用 Tab。密码含 `#`、`:`、`$` 时要正确引用并注意 Compose 插值。

### 环境变量没有更新

`docker compose restart` 不重新创建容器环境。修改 `.env` 后使用：

```bash
SERVICE_NAME='polaris-wvp'
docker compose up -d --force-recreate "$SERVICE_NAME"
```

示例重建 WVP；其他服务要把变量值改为 `docker compose config --services` 输出中的实际名称。

## 6. MySQL 首次初始化失败

### 6.1 先看完整日志

```bash
docker compose logs --no-color polaris-mysql > /tmp/wvp-mysql-init.log
tail -300 /tmp/wvp-mysql-init.log
```

`/tmp/wvp-mysql-init.log` 是排错临时文件，处理结束后删除，避免留存敏感日志。

识别失败发生在：基础 `01-wvp.sql`、授权 expand `02`、最终结构 `03`，还是目录权限/磁盘/参数。

### 6.2 为什么重启不重跑 SQL

MySQL 一旦创建系统数据文件，`/var/lib/mysql` 就非空。官方入口只对空目录执行初始化脚本。即使第二段 SQL 失败，重启也通常不会重新执行它。

检查目录：

```bash
sudo find volumes/mysql/data -mindepth 1 -maxdepth 1 -print | head
```

### 6.3 只有真正“从未投入使用”的失败库才能重新初始化

必须同时满足：

- 这是本次全新部署的第一次失败；
- 没有任何用户/设备/业务数据；
- 已保存完整初始化日志和当前失败目录；
- 已修复 SQL、权限、密码或磁盘根因。

采用“改名隔离”，不要直接删除：

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose down
sudo mv volumes/mysql/data \
  "volumes/mysql/data.failed.$(date +%Y%m%d-%H%M%S)"
sudo install -d -m 0750 volumes/mysql/data
docker compose up -d polaris-mysql
docker compose logs -f --tail=300 polaris-mysql
```

如果目录属主由 MySQL 容器 UID 管理，新建空目录可能由容器初始化时调整；权限失败时先看容器实际 UID和 SELinux，不使用 `chmod 777`。

任何已经使用过的数据库都不能按这套步骤处理；应从备份恢复或做针对性数据库修复。

### 6.4 密码修改后 WVP 连不上

全新初始化前，MySQL `MYSQL_PASSWORD` 与 WVP `DATABASE_PASSWORD` 必须相同。初始化后只改环境变量不会修改库内用户密码。

检查 WVP 报错是 access denied 还是 connection refused：

- `access denied`：用户名/密码/授权；
- `connection refused`/timeout：MySQL 未就绪、容器网络或服务状态；
- `Unknown database wvp`：初始化未完成或库名错。

## 7. WVP 循环重启

```bash
docker compose ps
docker inspect "$(docker compose ps -q polaris-wvp)" \
  --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} restarts={{.RestartCount}}'
docker compose logs --tail=500 polaris-wvp
```

按顺序检查：

1. MySQL、Redis、Media 是否 running/healthy；
2. 数据库账号密码；
3. 两个 pepper 长度/空值；
4. JWK/config 挂载是否可写；
5. SIP 端口是否被占用；
6. ZLM secret 两处是否一致；
7. `application-docker.yml` YAML 是否正确；
8. 是否 OOM；
9. 授权模式是否误设为 production 不允许的 `disabled`；
10. Java 堆转储路径/磁盘是否满。

依赖启动早期的短暂重启与持续循环不同。MySQL healthy 后 WVP 仍反复退出才是待处理故障。

## 8. 页面打不开、502 或静态页面异常

### 8.1 从内到外检查

服务器本机：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
docker compose ps polaris-nginx polaris-wvp
docker compose logs --tail=200 polaris-nginx
docker compose logs --tail=300 polaris-wvp
```

- 本机 200、外部不通：安全组、防火墙、NAT、域名；
- 本机连接拒绝：Nginx 容器/端口映射；
- 页面出来但 `/api/` 502：WVP 未就绪或内层 upstream 失败；
- 页面旧、功能不匹配：前端镜像没有重建或浏览器缓存；
- 登录后页面空白：浏览器控制台、动态路由、API 401/403和角色菜单。

### 8.2 HTTPS 502

宿主机 Nginx：

```bash
sudo nginx -t
sudo journalctl -u nginx -n 200 --no-pager
curl -I http://127.0.0.1:8080/
```

CentOS/Rocky 若日志含 SELinux 拒绝代理网络，检查：

```bash
sudo ausearch -m AVC -ts recent
getsebool httpd_can_network_connect
```

确认为宿主机 Nginx 需要连接本机 8080 时，可保持 SELinux enforcing 并允许该能力：

```bash
sudo setsebool -P httpd_can_network_connect 1
```

不要永久关闭 SELinux。

### 8.3 WebSocket 失败

检查外层和内层都传递：

- HTTP/1.1；
- `Upgrade`；
- `Connection: upgrade`；
- `Sec-WebSocket-Protocol`；
- 足够的 read timeout。

`/channel/log` 使用短期一次性 ticket，拿普通 URL 直接刷新失败不代表 WebSocket 代理必然坏；从页面实际流程和服务日志判断。

## 9. 登录失败、401 与 403

### 9.1 初始 `admin/admin` 不工作

只适用于真正全新库。检查：

```bash
docker compose exec polaris-mysql mysql -u root -p wvp
```

```sql
SELECT username, password_version, status, locked, deleted
FROM wvp_user WHERE username='admin';
```

- 没有 admin：基础 SQL 未完成；
- `status/locked/deleted` 非正常：账号状态问题；
- 已 `password_version=1`：说明密码曾被升级，默认 `admin` 不再适用；
- 不要把数据库摘要当作可登录密码。

### 9.2 401

通常表示没有有效身份：token 缺失、过期、用户停用、密码/角色变更导致 token version 变化、JWK 变化。先重新登录，不要把 token 加到 URL。

### 9.3 403

表示已登录但权限不足。检查：

- 用户是否分配正确角色；
- 角色是否启用；
- 功能/菜单权限是否包含当前操作；
- 部门数据范围是否允许目标组织数据；
- 视频范围是否包含目标视频资源组；
- 设备/通道是否实际绑定资源组；
- 是否处于 enforce；
- 多角色是否按预期组合。

按钮隐藏只是前端体验，后端 403/404才是安全边界。

## 10. 用户看不到摄像机：先分部门与视频范围

按顺序：

1. 超级管理员是否能看到设备；
2. 设备和通道是否在线；
3. 设备/通道是否绑定到视频资源组；
4. 角色视频范围是 `ALL`、`CUSTOM` 还是 `NONE`；
5. `CUSTOM` 是否选中了正确父/子资源组；
6. 角色是否有设备/通道列表和播放功能权限；
7. 用户是否真的拥有该角色；
8. 重新登录后是否更新动态菜单/token；
9. 查看 `wvp_authz_audit` 的 reason_code。

不要通过修改用户部门来“让摄像机出现”。部门控制组织数据范围，摄像机可见性由视频资源组决定。

## 11. GB28181 设备注册不上

### 11.1 包完全没到服务器

```bash
sudo tcpdump -ni any port 8160
```

查设备平台地址/端口、网关、协议、安全组、防火墙、NAT、CGNAT。

### 11.2 REGISTER 到了但没有成功

同步看 WVP 日志和 SIP 包：

```bash
docker compose logs -f --tail=300 polaris-wvp
sudo tcpdump -ni any -A port 8160
```

查平台 ID、设备 ID、域、认证用户名/密码、时间、重复 ID、响应是否能返回设备。

### 11.3 注册后很快离线

查心跳/重新注册、NAT 超时、TCP 连接断开、WVP 重启、Redis/MySQL、设备网络抖动。

不要把设备页面“注册成功”作为唯一证据；WVP 列表和持续心跳也要正常。

## 12. 设备在线但没有通道

这是 Catalog 阶段，不是 RTP 阶段。

1. 平台是否发 Catalog MESSAGE；
2. 设备是否收到并返回；
3. 返回是否分包，SN/设备 ID是否匹配；
4. XML 是否可解析；
5. 设备是否为视频通道启用国标；
6. 通道 ID是否唯一；
7. WVP 数据库保存是否报约束/编码错误；
8. NVR 下游摄像机是否在线。

开放 `10003` 不能解决目录消息没有返回。

## 13. 点播超时：四段定位

### 13.1 WVP 是否发出 INVITE

没有 INVITE：用户权限、通道状态、设备状态、播放 API、WVP业务日志。

### 13.2 设备是否返回 200 OK

没有返回：SIP 回程、设备会话能力、通道 ID、TCP/UDP、设备忙或拒绝原因。

### 13.3 RTP 是否到达 `10003`

```bash
sudo tcpdump -ni any port 10003
```

没有包：检查 SDP_IP、MediaRtp、NAT、设备媒体传输模式、安全组、运营商 UDP/TCP。

### 13.4 ZLM 是否注册流

```bash
docker compose logs --since=10m polaris-media
```

有 RTP 没流：查协议模式、SSRC、payload、PS 封装、音视频轨道、ZLM 配置端口和 Hook。

ZLM 有流、浏览器没画面：查临时播放票据、`Stream_IP`、Nginx `/rtp/`、HTTP/HTTPS mixed content、浏览器编码。

## 14. 黑屏、有声无画、无声或卡顿

按顺序降低变量：

1. 单设备、单通道；
2. 子码流；
3. H.264；
4. G.711A/PCMA；
5. 降分辨率/码率；
6. 缩短 GOP；
7. 局域网直连测试；
8. 对照 ZLM 音视频轨道信息；
9. 检查浏览器控制台和网络；
10. 检查网卡丢包、带宽和 MTU。

只有声音通常说明视频编码/关键帧/轨道问题；只有画面可能是音频编码或浏览器自动播放策略。花屏卡顿常与 UDP 丢包、带宽、关键帧和设备编码有关。

## 15. RTSP/RTMP 推拉流失败

先确认谁主动连接谁：

- 平台拉摄像机 RTSP：服务器到摄像机的出站路由、账号、URL、摄像机 RTSP 端口；
- 外部编码器推 RTMP：编码器到平台 `10001/tcp`、应用/流名和授权；
- 外部客户端连平台 RTSP：客户端到 `10002`、ZLM 实际监听、鉴权和来源限制。

检查 `.env` 和 `media/config.ini` 的端口是否同步：

```bash
grep -E '^(MediaRtmp|MediaRtsp|MediaRtp)=' .env
grep -n -E '^\[(rtmp|rtsp|rtp_proxy)\]|^port=' media/config.ini
docker compose port polaris-media 10001
docker compose port polaris-media 10002
docker compose port polaris-media 10003
```

如果你改了容器内部端口，`docker compose port` 参数也要换成实际容器端口。

## 16. ZLMediaKit 媒体节点离线或 API 鉴权失败

检查两处 secret：

```bash
grep -n 'ZLM_SERCERT' docker-compose.yml
grep -n '^secret=' media/config.ini
```

不要把输出发到公共工单。两者必须一致。

再检查：

```bash
docker compose ps polaris-media polaris-wvp
docker compose logs --tail=300 polaris-media
docker compose logs --tail=300 polaris-wvp
docker network inspect docker_media-net 2>/dev/null || true
```

Compose 网络名可能带项目名前缀，以 `docker network ls` 实际输出为准。WVP 访问 ZLM 用容器名 `polaris-media:80`，不是宿主机 `Stream_IP`。

## 17. 文件权限和 SELinux

### 17.1 普通 Unix 权限

```bash
ls -ld docker docker/logs docker/volumes docker/wvp/config
docker compose logs --tail=200 polaris-mysql
```

找实际的 `Permission denied` 路径和容器 UID。只调整需要写入的目录所有者/模式，不对项目根目录 `chmod -R 777`。

### 17.2 SELinux

```bash
getenforce
ls -Zd /opt/wvp-GB28181-pro/docker/volumes
sudo ausearch -m AVC -ts recent
```

确认是容器 bind mount 标签问题后，可以在 Compose 挂载项中使用 SELinux relabel 选项：

- `:z`：目录需要被多个容器共享，例如 `volumes/video`；
- `:Z`：仅一个容器私有使用的目录。

修改前要理解当前挂载是否多容器共享，错误使用 `:Z` 可能让另一个容器失去访问。参考：[Compose volumes](https://docs.docker.com/reference/compose-file/services/#volumes)。

也可由 SELinux 管理员使用 `semanage fcontext` + `restorecon` 建立持久规则。`chcon` 的临时标签可能在 relabel 后丢失。不要将 SELinux 永久设为 disabled。

## 18. 端口冲突

```bash
sudo ss -lntup | grep -E ':(8080|8160|10001|10002|10003)\b'
docker compose ps
```

识别是宿主机程序、另一套 Compose，还是旧容器。不要随意 kill 不认识的生产进程。

如果改端口，按 [04 配置说明](04-configuration.md) 同步 `.env`、ZLM config、防火墙、安全组、NAT、设备和访问地址。

## 19. 磁盘满或 inode 满

```bash
df -hT
df -ih
du -xhd1 /var/lib/docker 2>/dev/null | sort -h
du -xhd2 /opt/wvp-GB28181-pro/docker 2>/dev/null | sort -h | tail -30
```

常见来源：录像、MySQL、Docker JSON 日志、应用日志、旧镜像/构建缓存、堆转储。

处理顺序：

1. 停止继续增长的非关键写入；
2. 备份现场和关键数据；
3. 按保留策略归档日志/录像；
4. 确认未被使用的镜像/缓存；
5. 扩容或迁移数据盘；
6. 修复轮转和告警。

不要先删 MySQL 数据或当前录像来换取几分钟空间。

## 20. 防火墙看似开放但仍不通

三层分别查：

1. 云安全组/运营商；
2. 路由器 NAT/上级网关；
3. Linux firewalld/UFW/Docker 规则。

Docker 发布端口可能绕过 UFW 常规 INPUT 规则，所以“UFW deny”不能证明容器端口对公网不可达。要从真实外部网络测试，并用云安全组限制来源。

UDP 用 `nc -zvu` 不能证明双向成功。设备真实操作时配合 `tcpdump` 看入包和回包。

## 21. HTTPS 页面能开但播放失败

浏览器开发者工具检查媒体 URL：

- 是否仍是 `http://` 或 `ws://`；
- host 是否为 `127.0.0.1`/内网 IP；
- 端口是否仍是 8080；
- ticket 是否存在但已过期；
- `/rtp/` 是否 401/403/404/502；
- WebSocket 是否升级。

配置核对：

```bash
grep -E '^(WebHttp|WebHttps|Stream_IP)=' .env
sed -n '/^\[http\]/,/^\[/p' media/config.ini
grep -n 'MediaHttps' docker-compose.yml
sudo nginx -t
```

生产 TLS 模式下，ZLMediaKit 的 `[http] sslport` 应为 443，`MediaHttps` 应保持注释；WVP 会读取 ZLMediaKit 上报端口并优先生成 HTTPS 地址。外层代理要把页面、API、WebSocket 和 `/rtp/` 转给内层 Nginx，且 `/index/hook/` 仍返回 404。

若页面从 `8080` 打开但票据是 `https://域名:443/rtp/...`，这不是端口随机变化：8080 是管理入口，443 是媒体票据使用的安全入口。没有真实 443 TLS 监听时，请先完成第 07 章，不要取消 `MediaHttps` 注释碰运气。

## 22. 临时文件清理

排错中如果按本章创建了 `/tmp/wvp-mysql-init.log`，确认已保存必要证据并脱敏后清理：

```bash
rm -f /tmp/wvp-mysql-init.log
```

不要使用通配符删除 `/tmp`，不要清理不属于本次排错的文件。抓包文件同样应按敏感数据管理并及时移走/删除。

## 23. 提交故障信息的最小集合

脱敏后提供：

- 操作系统、CPU 架构、Docker/Compose 版本；
- 代码版本；
- 网络拓扑图和失败方向；
- `.env` 变量名及地址/端口（密钥用 `***`）；
- `docker compose ps`；
- 相关服务故障时间前后的日志；
- `ss -lntup` 和防火墙/安全组规则摘要；
- 设备品牌、型号、固件、SIP/RTP 模式；
- 注册/目录/点播哪个阶段失败；
- 抓包中是否看到 REGISTER、Catalog、INVITE 和 RTP；
- 最近一次正常时间与变更记录。

不要提供：真实密码、pepper、JWK 私钥、完整 API Key、播放 ticket、未脱敏 SIP 摘要认证、数据库备份。

[上一章：日常运维与备份](08-operations-and-backup.md) · [返回首页](README.md) · [下一章：验收清单](10-acceptance-checklist.md)
