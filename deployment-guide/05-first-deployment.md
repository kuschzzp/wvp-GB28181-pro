# 05 首次部署

[上一章：配置说明](04-configuration.md) · [返回首页](README.md) · [下一章：GB28181 设备接入](06-gb28181-device-access.md)

本章面向全新服务器、全新 MySQL 数据目录。操作会实际构建并启动服务。开始前应已完成前四章，并确认有可恢复的 SSH 控制通道。

## 1. 上线前最后确认

### 1.1 确认代码目录正确

执行目录：项目根目录。

```bash
cd /opt/wvp-GB28181-pro
test -f pom.xml && test -f docker/docker-compose.yml && echo "项目结构正确"
git status --short
```

第一条应输出 `项目结构正确`。`git status` 用于确认服务器拿到的确实是预期改造版本；如果部署包不含 `.git`，它报“not a git repository”不影响文件本身，但你要用发布包校验值或版本记录确认来源。

不要为了部署方便临时换成另一个上游仓库，否则用户、角色、部门、视频资源组和安全接口可能与本手册不一致。

### 1.2 检查构建上下文大小

当前 Compose 从项目根目录构建 WVP 和前端。若你把本地 `node_modules`、`target`、历史日志或录像一起上传，首次构建上下文会异常庞大。

```bash
cd /opt/wvp-GB28181-pro
du -sh .
du -sh web/node_modules target docker/logs docker/volumes 2>/dev/null
```

全新源代码部署通常不需要预先带 `web/node_modules`、`target`、运行日志或录像。发现异常大目录时先确认它是否属于发布内容，不要盲目删除生产或用户数据。

### 1.3 确认 MySQL 是全新目录

执行目录：Compose 目录。

```bash
cd /opt/wvp-GB28181-pro/docker
sudo find volumes/mysql/data -mindepth 1 -maxdepth 1 -print 2>/dev/null
```

全新部署的正常结果是没有输出，或目录尚不存在。如果出现 `ibdata1`、`mysql/`、`wvp/` 等内容，说明数据库已经初始化过。先停止，不要把它当作全新库继续，也不要删除；到 [09 故障排查](09-troubleshooting.md) 的“MySQL 初始化”部分判断来源。

### 1.4 校验配置但不展开秘密

```bash
docker compose config --quiet
docker compose config --services
docker compose config --images
```

预期服务名：

```text
polaris-redis
polaris-mysql
polaris-media
polaris-wvp
polaris-nginx
```

顺序可能不同。`config --quiet` 无输出并返回 0 才继续。

### 1.5 检查磁盘与端口

```bash
df -hT .
sudo ss -lntup | grep -E ':(8080|8160|10001|10002|10003)\b' || true
```

没有输出表示当前没有程序监听这些默认端口。若有输出，识别进程并处理冲突，不能靠反复重启 Docker 解决。

## 2. 建议分阶段下载和构建

一条 `docker compose up -d --build` 可以完成全部工作，但首次部署建议分阶段，这样能看出失败属于镜像拉取、apt、Maven 还是 npm。

### 2.1 拉取 Compose 直接使用的三个镜像

执行目录：Compose 目录。

```bash
docker compose pull polaris-redis polaris-mysql polaris-media
```

正常结果：三个服务的镜像下载完成或显示已是最新。若出现 `TLS handshake timeout`、`i/o timeout`、`403` 或标签不存在，先按 [02 服务器准备](02-server-preparation.md) 检查账号加速器/私有仓库，不要立即改 Maven 配置。

### 2.2 准备两个本地构建的基础镜像

如果 Compose/Dockerfile 仍使用原始 Docker Hub 名称：

```bash
docker pull ringcentral/jdk:21.0.9
docker pull ubuntu:24.04
docker pull nginx:alpine
```

如果已经换成自己的 ACR/TCR/SWR 地址，拉取相应私有引用，不要再拉原始标签。

记录摘要，便于以后重现：

```bash
docker image inspect ringcentral/jdk:21.0.9 --format '{{json .RepoDigests}}'
docker image inspect ubuntu:24.04 --format '{{json .RepoDigests}}'
docker image inspect nginx:alpine --format '{{json .RepoDigests}}'
```

浮动标签 `redis:latest`、`zlmediakit/zlmediakit:master` 也建议记录摘要。标签名称相同不保证半年后内容相同。

### 2.3 构建 WVP 镜像

```bash
docker compose build --progress=plain polaris-wvp
```

这个阶段会：

1. 使用 `ringcentral/jdk:21.0.9`；
2. 在容器内通过 apt 安装 Maven；
3. 复制项目源码；
4. 执行 `mvn clean package -Dmaven.test.skip=true`；
5. 生成运行镜像。

第一次可能需要下载大量 Java 依赖，耗时取决于国内线路、镜像缓存、CPU 和磁盘，不能用固定分钟数判断是否卡死。看最后正在访问的 URL 和构建步骤：

- `apt-get update` 失败：容器内 Debian 软件源问题；
- `Could not resolve dependencies`：Maven/具体依赖仓库问题；
- `maven.ecc.no` 失败：独立 ECC 仓库问题，不是阿里 Maven Central 镜像能完全替代；
- Java 编译错误：代码或版本问题，不是网络加速问题。

### 2.4 构建 Nginx/前端镜像

```bash
docker compose build --progress=plain polaris-nginx
```

这个阶段会：

1. 使用 `ubuntu:24.04`；
2. 通过 apt 安装 Node.js 和 npm；
3. 通过 `https://registry.npmmirror.com` 安装前端依赖；
4. 执行生产前端构建；
5. 把静态文件复制到 `nginx:alpine`。

`apt-get` 失败和 `npm install` 失败是两条不同链路。报错 URL 指向 Ubuntu 仓库时，改 npm registry 没有帮助。

## 3. 启动五个服务

构建成功后：

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose up -d
```

如果你跳过了分阶段构建，也可以：

```bash
docker compose up -d --build
```

正常结果是 Compose 创建网络、容器并返回终端。`-d` 代表后台运行。

当前 `depends_on` 只规定启动顺序，没有等待 MySQL/Redis 健康后再启动 WVP 的条件。首次数据库初始化期间，WVP 可能暂时连接失败并由 `restart: always` 重启。不要只看启动后第一秒的状态。

## 4. 观察启动过程

### 4.1 查看总状态

```bash
docker compose ps
```

期望：五个服务都为 `Up`/`running`；Redis 和 MySQL 经过初始化后显示 healthy。若 WVP 的重启次数持续增加，转到服务日志。

持续观察状态：

```bash
watch -n 2 'docker compose ps'
```

按 `Ctrl+C` 退出 `watch`，不会停止容器。

### 4.2 MySQL 日志

```bash
docker compose logs --tail=300 polaris-mysql
```

正常特征：完成数据目录初始化、按顺序执行初始化 SQL，最终可以接受连接；不能出现 SQL 语法错误、外键失败、contract refused 或反复初始化崩溃。

### 4.3 Redis 日志

```bash
docker compose logs --tail=150 polaris-redis
```

正常特征：配置加载成功并进入可接受连接状态，没有 AOF 权限错误。

### 4.4 ZLMediaKit 日志

```bash
docker compose logs --tail=250 polaris-media
```

正常特征：配置文件可读、HTTP/RTSP/RTMP/RTP 对应端口能监听，没有 config 解析或目录权限错误。

### 4.5 WVP 日志

```bash
docker compose logs --tail=400 polaris-wvp
```

正常特征：Spring 应用完成启动、MySQL/Redis 连接建立、SIP TCP/UDP 监听成功、媒体节点能被配置。第一次启动早期出现短暂依赖连接失败可以观察，但若一直重启就必须处理。

### 4.6 Nginx 日志

```bash
docker compose logs --tail=150 polaris-nginx
```

正常特征：配置加载成功，没有 upstream 语法、模板或静态目录错误。

需要实时跟踪某个服务时：

```bash
docker compose logs -f --tail=200 polaris-wvp
```

按 `Ctrl+C` 只停止日志跟踪，不停止服务。

## 5. 理解三段数据库初始化

MySQL 官方镜像会按文件名顺序执行 `/docker-entrypoint-initdb.d/`：

1. `01-wvp.sql`：WVP 基础业务表和初始管理员；
2. `02-wvp-authorization.sql`：用户多角色、部门、菜单/功能权限、视频资源组、审计、API Key 和媒体安全等授权结构与种子；
3. `03-wvp-authorization-final.sql`：把全新数据库收缩到当前最终 RBAC 表结构，保留新应用需要的兼容视图。

虽然第二、三段文件中保留了迁移语义和防护检查，在本手册的全新部署中它们由 Compose 在空数据库上顺序执行。不要手工跳过第三段，也不要把旧版本数据库直接塞进来冒充全新初始化。

这些脚本只在 MySQL 数据目录为空时自动执行。任何一段失败后，数据目录通常已经非空；重启容器不会自动从失败处继续。此时先保存完整 MySQL 日志，到 [09 故障排查](09-troubleshooting.md) 按“首次初始化失败”处理。

## 6. 静态验证数据库结构

数据库 healthy 后，进入 MySQL 客户端。命令会提示输入你在 Compose 中设置的 root 密码，不要把密码直接放在命令行参数中：

```bash
docker compose exec polaris-mysql mysql -u root -p wvp
```

进入 `mysql>` 后执行：

```sql
SHOW TABLES LIKE 'wvp_dept';
SHOW TABLES LIKE 'wvp_role';
SHOW TABLES LIKE 'wvp_resource_group';
SHOW TABLES LIKE 'wvp_authz_audit';
SHOW FULL TABLES WHERE Tables_in_wvp = 'wvp_user_role_rel';
SELECT username, password_version, status, locked, deleted
FROM wvp_user
WHERE username = 'admin';
```

预期：

- 四张核心表都存在；
- `wvp_user_role_rel` 是可用兼容视图；
- 初次登录前 `admin` 的 `password_version` 可以是 `0`；
- `status`、`locked`、`deleted` 都为正常值 `0`。

退出：

```sql
exit
```

不要在共享终端里查询或复制密码摘要。

## 7. 验证 Web 入口

先在服务器本机：

```bash
curl -sS -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8080/
```

如果修改了 `WebHttp`，替换端口。正常应返回 `200` 或可解释的成功 HTTP 状态。

然后从管理电脑浏览器访问：

```text
http://服务器可达地址:8080/
```

本机成功、远程失败，优先查安全组、防火墙、NAT 和访问地址；两边都失败再查 Nginx/Compose。

这里验证的是页面和 API 的 HTTP 管理入口，不代表受保护媒体地址已经可用。当前播放票据优先生成 ZLMediaKit 上报的 `HTTPS:443` 地址；正式验证浏览器播放前，请完成 [07 域名、HTTPS 与 NAT](07-domain-https-and-nat.md)，或使用其中明确限定为可信局域网的 HTTP 实验模式。

## 8. 首次登录与密码升级

全新初始化的初始账号：

```text
用户名：admin
密码：admin
```

这是一次性引导凭证，不能长期使用。当前登录接口接收原始密码；数据库中的旧 MD5 摘要在成功登录后会自动升级为 BCrypt。`legacy-md5-login-enabled=false` 禁用的是旧式“客户端先传 MD5”的兼容接口，不影响用原始 `admin` 密码完成首次登录。

登录后立即：

1. 点击右上角用户菜单；
2. 选择“修改密码”；
3. 设置独立强密码；
4. 退出并用新密码重新登录；
5. 不再把 `admin/admin` 作为排错尝试。

可再次进入 MySQL，只查版本字段：

```sql
SELECT username, password_version FROM wvp_user WHERE username = 'admin';
```

预期 `password_version=1`。不要把 BCrypt 摘要复制出来作为“备份”；正确备份是 MySQL 逻辑备份。

## 9. 全新平台的 RBAC 初始化顺序

部门和视频资源组必须分开理解：

- 部门解决“用户属于哪个组织，以及角色能管理哪些部门用户/组织数据”；
- 视频资源组解决“角色能看到和操作哪些设备、通道、推流、拉流等视频资源”；
- 摄像机不因为用户属于某部门就自动属于该部门；两套范围在角色上分别配置。

建议顺序：

### 9.1 建部门

在“系统管理 → 部门管理”中建立真实组织树，例如总部、分公司、运维组。不要用摄像机安装位置冒充部门，除非它确实也是组织结构。

### 9.2 建视频资源组

在“系统管理 → 视频资源组”中建立资源树，例如华东区域、某园区、某项目。当前还没接入设备时可以先建空组；设备接入后再把整台设备或通道绑定进去。

父资源组授权会动态包含下级组；授权整台设备时，可包含以后同步的新通道。实际保存前仍要阅读页面提示并做普通用户验证。

### 9.3 建角色

在“系统管理 → 角色管理”中分别设置：

1. 功能/菜单权限：能进入哪些页面、执行哪些按钮操作；
2. 部门数据范围：全部、指定部门、本部门、本部门及下级、仅本人或无数据；
3. 视频范围：全部、指定视频资源组或无视频数据。

一个用户可以拥有多个角色，最终能力通常是多角色范围的并集。不要给“只看监控”的用户顺手授予系统管理、用户修改或资源组分配权限。

### 9.4 建普通用户

在“系统管理 → 用户管理”中：

- 选择用户的主部门；
- 分配一个或多个角色；
- 设置初始密码并要求首次使用后修改；
- 不要共享 `admin` 账号；
- 停用离职/停用用户，而不是只从通讯录删除名字。

### 9.5 用普通用户做正反向测试

正向测试：能看到被授权菜单、部门数据和视频资源，能执行角色允许的点播/控制。

反向测试：看不到未授权菜单；直接访问未授权 API/对象应在 enforce 模式返回 403 或按对象存在性策略返回 404；不能只检查“页面有没有隐藏按钮”。

## 10. 从 audit 切换到 enforce

全新部署仍建议先保持 `audit`，完成用户、角色、部门、视频资源组和至少一台设备的完整业务验证。audit 会记录真实决策，但部分受灰度控制业务保持兼容放行，所以它不是最终隔离状态。

查看近期审计汇总：

```bash
docker compose exec polaris-mysql mysql -u root -p wvp
```

```sql
SELECT decision, reason_code, permission_code, COUNT(*) AS total
FROM wvp_authz_audit
WHERE create_time >= NOW() - INTERVAL 1 DAY
GROUP BY decision, reason_code, permission_code
ORDER BY decision, total DESC;
```

每个 `DENY` 都要能解释：是你主动做的反向测试，还是角色遗漏。不要为了让数字归零而给角色授予超出职责的权限。

准备切换时：

```bash
cd /opt/wvp-GB28181-pro/docker
cp -a wvp/wvp/application-docker.yml \
  "wvp/wvp/application-docker.yml.before-enforce.$(date +%Y%m%d-%H%M%S)"
```

把：

```yaml
authorization:
    mode: audit
```

改为：

```yaml
authorization:
    mode: enforce
```

然后只重启 WVP：

```bash
docker compose restart polaris-wvp
docker compose logs --tail=300 polaris-wvp
docker compose ps
```

重新执行管理员、普通用户、无权限用户、设备点播、录像、下载、WebSocket 日志和 API Key 的正反向测试。出现权限遗漏时，优先明确遗漏并修正角色；需要快速恢复业务时可回切 `audit` 并重启 WVP，已配置的权限关系和审计数据不会因此丢失。

不要使用 `disabled` 作为生产回退模式。

## 11. 记录第一次部署证据

至少保存：

- 发布代码版本/校验值；
- `docker compose config --images` 输出和镜像摘要；
- 首次启动时间；
- 五个服务的最终状态；
- MySQL 三段初始化成功的日志片段；
- 初始密码已修改的确认，不记录新密码明文；
- audit 观察时间、已解释的 DENY；
- enforce 切换时间及验证人；
- 备份位置和第一次恢复演练计划。

## 12. 本章验收

- [ ] 构建上下文没有混入不必要的大文件；
- [ ] MySQL 首次启动前数据目录为空；
- [ ] 镜像拉取、WVP 构建、前端构建分别成功；
- [ ] 五个服务持续运行，MySQL/Redis healthy；
- [ ] 三段 SQL 全部成功，最终 RBAC 表/视图存在；
- [ ] Web 本机和实际管理电脑都能访问；
- [ ] 已用 `admin/admin` 完成唯一一次引导并立即改密；
- [ ] 部门与视频资源组按不同目的建立；
- [ ] 普通用户已通过角色获得最小权限；
- [ ] audit DENY 已解释，enforce 切换有验证和回退记录。

[上一章：配置说明](04-configuration.md) · [返回首页](README.md) · [下一章：GB28181 设备接入](06-gb28181-device-access.md)
