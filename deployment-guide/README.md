# WVP 单机 Docker 部署学习手册

> 适用项目：当前仓库中的 WVP、ZLMediaKit、MySQL、Redis、Nginx 一体化 Compose 部署
>
> 适用场景：全新部署，不处理旧数据库升级
>
> 主要系统：CentOS Stream / Rocky Linux；同时提供 Ubuntu 命令
>
> 网络环境：中国大陆，包含国内下载源与镜像失效回退
>
> 核对日期：2026-07-16

这套手册写给第一次部署视频平台的人。你不需要提前懂 Docker、SIP、RTP 或 NAT，但需要有一台自己能管理的 Linux 服务器，并且知道摄像机是否能通过网络访问这台服务器。

本手册使用当前仓库的实际配置，不是通用 WVP 教程。当前部署会启动五个服务：

- WVP：设备管理、GB28181 信令、用户登录、RBAC 权限和业务接口；
- ZLMediaKit：接收 RTP/RTSP/RTMP 媒体并输出浏览器可播放的流；
- MySQL：保存用户、权限、设备、通道和业务数据；
- Redis：保存缓存及部分协同状态；
- Nginx：对外提供管理页面、API、WebSocket 和受保护的媒体入口。

## 先看这六条

1. 所有命令都标有“执行目录”。不确定时不要在任意目录直接执行。
2. 示例中的 `192.168.10.20`、`203.0.113.20` 和 `198.51.100.20` 都是文档地址，必须换成你的实际地址。
3. 示例文件不会被 Compose 自动读取。真正生效的是 [`docker/.env`](../docker/.env)。
4. `docker compose down` 通常不会删除本项目的 bind mount 数据，但不要手工删除 `docker/volumes/mysql/data`。
5. 中国大陆下载要区分“Docker 软件安装源”“容器镜像源”和“Maven/npm 依赖源”。它们互相不能替代。
6. 当前 Compose 只发布 HTTP 管理入口，但受保护播放地址会按 ZLMediaKit 上报值优先使用 `HTTPS:443`；正式验收浏览器播放前必须完成 [07 域名、HTTPS 与 NAT](07-domain-https-and-nat.md) 的 TLS 入口，或明确采用其中的纯内网 HTTP 实验模式。HTTPS 仍不会自动解决 GB28181 的 SIP/RTP 穿透问题。

## 你需要准备什么

- 一台 64 位 Linux 服务器，建议从 4 核 CPU、8 GB 内存起步；
- 足够的系统盘空间，以及按录像保留期单独规划的录像空间；
- `root` 或具有 `sudo` 权限的账号；
- 服务器内网 IP；如果跨公网接入，还需要公网 IP、端口映射权限或云安全组权限；
- 当前这份项目代码的获取方式；
- 至少一台支持 GB28181 的设备，以及设备管理密码；
- 为平台规划好的 GB28181 域、平台 ID、设备 ID 和认证密码。

4 核 8 GB 只是便于学习和小规模验证的起点，不代表固定能承载多少路。真正容量取决于码率、并发播放、录像、转码、磁盘性能和网络带宽。

## 推荐阅读顺序

| 顺序 | 章节 | 读完应该得到什么 |
|---:|---|---|
| 1 | [01 架构与基础概念](01-architecture-and-concepts.md) | 明白 WVP 和 ZLMediaKit 各自负责什么 |
| 2 | [02 服务器准备](02-server-preparation.md) | 安装 Docker/Compose，并选好国内下载路径 |
| 3 | [03 网络与端口](03-network-and-ports.md) | 填好自己的网络参数表和开放端口 |
| 4 | [04 配置说明](04-configuration.md) | 正确填写 `.env`，生成并保存密钥 |
| 5 | [05 首次部署](05-first-deployment.md) | 构建、启动、检查并完成首次登录 |
| 6 | [06 GB28181 设备接入](06-gb28181-device-access.md) | 完成注册、目录同步和首次点播 |
| 7 | [07 域名、HTTPS 与 NAT](07-domain-https-and-nat.md) | 安全地处理公网、域名和端口映射 |
| 8 | [08 日常运维与备份](08-operations-and-backup.md) | 会看日志、重启、备份和恢复演练 |
| 9 | [09 故障排查](09-troubleshooting.md) | 按症状逐层定位问题 |
| 10 | [10 验收清单](10-acceptance-checklist.md) | 对部署、安全、设备和备份逐项验收 |

章节 05 的 `http://服务器:8080` 只用于首次登录和管理配置。当前项目的播放票据会优先生成 `https://Stream_IP:443/rtp/...`；如果还没有 443 TLS 入口，章节 06 可以先验证 SIP、RTP 和 ZLMediaKit 已经收到流，但不能把“浏览器播放失败”直接判断为收流失败。

## 最短部署路径

如果你希望先知道整体流程，再逐章执行，可以把下面这张表当作路线图。不要跳过链接中的说明直接复制命令。

- [ ] 确认操作系统、CPU 架构、时间、磁盘和 DNS；
- [ ] 安装 Docker Engine 和 Compose v2；
- [ ] 在中国大陆选择可验证的软件包源与容器镜像方案；
- [ ] 判断是局域网、直接公网 IP，还是路由器 NAT；
- [ ] 开放 Web、SIP 和 RTP 所需的 TCP/UDP 端口；
- [ ] 将 `docker/.env` 中的三个 `127.0.0.1` 改为设备真正可达的地址；
- [ ] 生成两个不同的 pepper，填入 `.env`；
- [ ] 从 `docker/` 执行 `docker compose config --quiet`；
- [ ] 首次构建并启动；
- [ ] 使用初始账号登录后立即修改密码；
- [ ] 先创建部门、角色、权限和视频资源组，再创建普通用户；
- [ ] 配置一台设备，依次验证注册、目录和点播；
- [ ] 完成一次 MySQL 备份并检查备份文件不为空。

## 命令中的目录约定

手册使用两个逻辑目录：

- `项目根目录`：能看到 `pom.xml`、`web/`、`src/` 和 `docker/` 的目录；
- `Compose 目录`：项目根目录下的 `docker/`。

假设你把项目放在 `/opt/wvp-GB28181-pro`，两者分别是：

```text
/opt/wvp-GB28181-pro
/opt/wvp-GB28181-pro/docker
```

手册中的“执行目录：项目根目录”对应：

```bash
cd /opt/wvp-GB28181-pro
```

“执行目录：Compose 目录”对应：

```bash
cd /opt/wvp-GB28181-pro/docker
```

如果你的实际路径不同，只替换路径，不要改变目录层级。Compose 的构建上下文依赖 `docker/` 位于项目根目录下面。

## 获取代码时不要弄错版本

这个仓库包含针对用户、角色、权限、部门和视频资源授权的改造。服务器必须拿到你的这份代码，不能为了方便而临时克隆一个未经改造的上游 WVP 仓库。

如果代码在你自己的 Git 仓库中，使用实际地址：

```bash
cd /opt
REPOSITORY_URL='REPLACE_WITH_YOUR_REPOSITORY_URL'
sudo git clone "$REPOSITORY_URL" wvp-GB28181-pro
sudo chown -R "$(id -un)":"$(id -gn)" /opt/wvp-GB28181-pro
```

`REPLACE_WITH_YOUR_REPOSITORY_URL` 是占位符，必须先换成实际地址。若使用压缩包或 `scp` 上传，也要保证解压后项目根目录里直接存在 `docker/`，而不是多套了一层同名目录。

检查目录结构：

```bash
cd /opt/wvp-GB28181-pro
test -f pom.xml && test -f docker/docker-compose.yml && echo "目录正确"
```

正常结果是输出 `目录正确`。没有输出就先检查路径，不要继续部署。

## 关于中国大陆下载

本项目首次构建至少会接触以下下载来源：

| 下载类型 | 当前会下载什么 | 在哪里配置 |
|---|---|---|
| Docker CE 软件包 | Docker Engine、Compose 插件 | Linux 的 dnf/apt 软件仓库 |
| 容器镜像 | Redis、MySQL、ZLMediaKit、Ubuntu、Nginx、RingCentral JDK | Docker daemon、Compose、Dockerfile |
| 容器内 apt | Maven、Node.js、npm | 两个构建阶段中的 Ubuntu/Debian apt 源 |
| Maven | Java 依赖和插件 | 当前 `pom.xml` 已含阿里云公共仓库，同时还有 ECC 仓库 |
| npm | 前端依赖 | Nginx 构建已显式使用 `registry.npmmirror.com` |

“装得上 Docker”不代表“拉得到镜像”，“拉得到镜像”也不代表“Maven 构建一定成功”。[02 服务器准备](02-server-preparation.md) 会给出分层判断方法。

## 安全边界

- 不要把 MySQL 3306、Redis 6379、WVP 内部 18978 或 ZLMediaKit 内部 HTTP 80 直接暴露到公网；
- 不要把 `/index/hook/` 反向代理到公网；
- 不要使用 `chmod -R 777` 解决权限问题；
- 不要为了省事关闭防火墙或永久关闭 SELinux；
- 不要把 pepper、数据库密码、ZLM secret、设备密码提交到公开仓库；
- 不要认为“容器显示 Up”就等于设备注册、媒体接收和播放全部正常；
- 不要在没有备份的情况下删除数据目录或替换长期使用的密钥。

## 遇到问题时如何记录

提问或排错前至少保存以下信息，并对密码、token、设备认证信息打码：

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose ps
docker compose logs --tail=200 polaris-wvp
docker compose logs --tail=200 polaris-media
docker compose logs --tail=200 polaris-mysql
```

另外记录：操作系统版本、服务器内网/公网拓扑、设备与服务器是否同网、失败发生在安装/拉镜像/构建/注册/目录/点播哪一步。这样比一句“播放不了”更容易定位。

下一章：[01 架构与基础概念](01-architecture-and-concepts.md)
