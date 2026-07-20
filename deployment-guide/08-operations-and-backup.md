# 08 日常运维与备份

[上一章：域名、HTTPS 与 NAT](07-domain-https-and-nat.md) · [返回首页](README.md) · [下一章：故障排查](09-troubleshooting.md)

部署完成只是开始。本章建立日常检查、配置变更、日志管理、备份和恢复演练的固定动作。

## 1. 每天/每周看什么

### 1.1 每日快速检查

执行目录：Compose 目录。

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose ps
df -hT
docker stats --no-stream
```

关注：

- 五个服务是否持续 running；
- MySQL、Redis 是否 healthy；
- WVP 是否频繁重启；
- 系统盘、MySQL 盘、录像盘和日志盘使用率；
- 某个容器内存/CPU 是否持续异常；
- 摄像机在线率、通道在线率和播放成功率。

### 1.2 每周检查

```bash
du -sh logs/* volumes/video volumes/mysql/data volumes/redis/data 2>/dev/null
docker system df
sudo journalctl -u docker --since '7 days ago' --no-pager | tail -200
```

`docker system df` 只报告 Docker 管理的镜像/层/卷；bind mount 的录像和数据库目录要用 `du`/`df` 看。

不要看到镜像占空间就直接执行 `docker system prune -a`。它可能删除下次回滚需要的镜像和构建缓存，并让中国大陆服务器重新跨网下载。

## 2. Compose 日常命令的区别

所有命令从 `docker/` 执行。

### 2.1 查看状态

```bash
docker compose ps
docker compose top
```

### 2.2 查看日志

```bash
docker compose logs --tail=200 polaris-wvp
docker compose logs --since=30m polaris-media
docker compose logs -f --tail=100 polaris-nginx
```

`-f` 持续跟踪，`Ctrl+C` 只退出日志，不停止容器。

### 2.3 停止但保留容器

```bash
docker compose stop
```

再启动：

```bash
docker compose start
```

适合短维护窗口，不重建容器。

### 2.4 重启服务

```bash
docker compose restart polaris-wvp
```

适合应用读取 bind-mounted 配置后需要重启。不重新解析 Compose 环境变量，也不使用新镜像。

### 2.5 重新创建

```bash
docker compose up -d --force-recreate polaris-wvp
```

适合 `.env`、Compose `environment` 或端口映射变化。它会重建指定容器，但 bind mount 数据仍在宿主机。

### 2.6 重新构建

```bash
docker compose build polaris-wvp
docker compose up -d polaris-wvp
```

适合 Java 源码或 WVP Dockerfile 变化。前端源码变化则构建 `polaris-nginx`。

### 2.7 `down` 的含义

```bash
docker compose down
```

它删除本 Compose 的容器和默认网络，但当前 MySQL、Redis、录像使用宿主机 bind mount，目录不会因此自动消失。参考：[Docker Compose down](https://docs.docker.com/reference/cli/docker/compose/down/) 和 [bind mounts](https://docs.docker.com/engine/storage/bind-mounts/)。

即便如此，日常不要加 `-v`，也不要把“容器没了”和“数据已备份”混为一谈。更不能手工删除 `volumes/mysql/data` 来解决应用错误。

## 3. 配置变更应该重启什么

| 变更 | 建议动作 | 还要验证 |
|---|---|---|
| `.env` 地址/端口/开关 | `up -d --force-recreate` WVP、Nginx、Media 中受影响服务 | Compose config、端口、设备、播放 |
| WVP YAML | `restart polaris-wvp` | WVP 日志、登录、权限 |
| ZLM `config.ini` | 重启 `polaris-media`，通常再确认 WVP 媒体节点 | 端口、Hook、点播 |
| Nginx 模板 | 重建/重启 `polaris-nginx`，取决于模板加载方式 | `nginx -t`、API、WebSocket、媒体 |
| Java 源码/pom | build + up WVP | 构建日志、数据库兼容、回归 |
| 前端源码/package | build + up Nginx | 登录、路由、按钮权限 |
| Compose 镜像名 | `pull` 后 `up -d` | 摘要、健康、回滚镜像 |
| MySQL 密码 | 先在数据库变更，再同步 Compose/WVP并重建 | 登录、WVP连接；不能只改环境变量 |
| pepper/JWK/ZLM secret | 按密钥轮换方案同时更新关联方 | API Key、Hook、令牌、媒体节点 |

变更前固定执行：备份 → `docker compose config --quiet` → 记录旧值/镜像摘要 → 变更 → 验证 → 保留回退窗口。

## 4. 日志和磁盘

### 4.1 两类日志

本项目同时可能有：

- Docker stdout/stderr 日志，由 Docker 日志驱动管理；
- `docker/logs/mysql`、`logs/media`、`logs/wvp`、`logs/nginx` 等 bind mount 文件日志。

两者都可能占满磁盘。检查：

```bash
docker info --format '{{.LoggingDriver}}'
sudo du -sh /var/lib/docker/containers 2>/dev/null
du -sh logs/* 2>/dev/null
```

### 4.2 Docker 日志轮转

若使用默认 `json-file` 且服务器没有统一策略，可以在 `/etc/docker/daemon.json` 中规划：

```json
{
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "100m",
    "max-file": "5"
  }
}
```

这只是片段。如果文件已有 `registry-mirrors`、`data-root` 等字段，必须合并成一个合法 JSON，不能覆盖。修改前备份并验证：

```bash
sudo cp -a /etc/docker/daemon.json /etc/docker/daemon.json.before-log-rotation
sudo dockerd --validate --config-file=/etc/docker/daemon.json
sudo systemctl restart docker
```

重启 Docker 会影响当前容器；安排维护窗口。默认日志选项通常只在新建容器时完整生效，之后需要有计划地 recreate。

### 4.3 不要这样清日志

- 不对整个项目执行 `chmod 777`；
- 不在服务正在写时随意删除当前日志文件；
- 不把 `/var/lib/docker` 当普通缓存目录手工清理；
- 不为了腾空间删除 MySQL 数据文件或未备份录像。

优先使用应用日志轮转、Docker 日志选项和系统 `logrotate`，并先在测试环境验证。

## 5. 建立备份目录

备份不能只放在项目同一块磁盘上。以下 `/srv/backups/wvp` 是本地暂存目录，最终还应复制到另一台服务器、对象存储或离线介质。

```bash
sudo install -d -m 0700 -o "$(id -un)" -g "$(id -gn)" /srv/backups/wvp
umask 077
```

每次备份创建日期目录：

```bash
BACKUP_DIR="/srv/backups/wvp/$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP_DIR"
printf '%s\n' "$BACKUP_DIR"
```

记住当前 shell 的 `BACKUP_DIR` 只在本次会话有效。重新登录后要重新设置，不要假设变量仍存在。

## 6. MySQL 逻辑备份

执行目录：Compose 目录。

```bash
cd /opt/wvp-GB28181-pro/docker
```

导出业务库：

```bash
docker compose exec -T polaris-mysql sh -c \
  'exec mysqldump --user=root --password="$MYSQL_ROOT_PASSWORD" \
  --single-transaction --routines --triggers --events \
  --set-gtid-purged=OFF --default-character-set=utf8mb4 wvp' \
  > "$BACKUP_DIR/wvp.sql"
```

这里使用容器已有的 `MYSQL_ROOT_PASSWORD` 环境变量，避免把真实密码写进宿主机 shell 历史。不要开启 shell 调试 `set -x`，也不要把命令输出交给会记录环境变量的包装脚本。

验证文件：

```bash
test -s "$BACKUP_DIR/wvp.sql" && echo "SQL 备份非空"
head -n 20 "$BACKUP_DIR/wvp.sql"
grep -m 3 -E '^(CREATE TABLE|INSERT INTO)' "$BACKUP_DIR/wvp.sql"
```

`mysqldump` 返回码也必须为 0：应在执行导出后立即检查 `echo $?`，不要等运行了其他命令再看。

压缩并生成校验值：

```bash
gzip -9 "$BACKUP_DIR/wvp.sql"
sha256sum "$BACKUP_DIR/wvp.sql.gz" > "$BACKUP_DIR/SHA256SUMS"
chmod 600 "$BACKUP_DIR/wvp.sql.gz" "$BACKUP_DIR/SHA256SUMS"
```

`--single-transaction` 适合 InnoDB 一致性读取，但备份期间仍应避免大规模结构变更。数据库备份成功不代表录像文件已经备份。

## 7. 配置和密钥备份

从 Compose 目录执行：

```bash
tar -czf "$BACKUP_DIR/wvp-config.tar.gz" \
  .env \
  docker-compose.yml \
  media/config.ini \
  nginx/templates \
  wvp/config \
  wvp/wvp \
  mysql/conf \
  redis/conf
chmod 600 "$BACKUP_DIR/wvp-config.tar.gz"
sha256sum "$BACKUP_DIR/wvp-config.tar.gz" >> "$BACKUP_DIR/SHA256SUMS"
```

这里包含 pepper、数据库配置、SIP 密码、JWK 和 ZLM secret，必须加密存储、限制访问并传到受控备份位置。普通部署记录只写备份位置和负责人，不写明文秘密。

另存版本证据：

```bash
git rev-parse HEAD > "$BACKUP_DIR/source-version.txt" 2>/dev/null || true
docker compose config --images > "$BACKUP_DIR/images.txt"
docker image ls --digests > "$BACKUP_DIR/local-image-digests.txt"
```

`images.txt` 不应包含密码，但仍应按运维资料管理。

## 8. 录像备份

录像通常远大于数据库。先决定：

- 录像是否属于必须备份的数据；
- 保存多少天；
- 是否复制全部文件还是使用存储快照；
- 备份窗口能否停止录制；
- 恢复时如何保持数据库元数据与文件一致。

不停机增量复制可以使用：

```bash
rsync -aH --info=progress2 \
  /opt/wvp-GB28181-pro/docker/volumes/video/ \
  /mnt/backup/wvp-video/
```

示例不带 `--delete`，避免源路径配置错误时删除备份端内容。活跃写入文件在复制过程中可能不一致，所以关键录像需要：文件系统/存储快照，或维护窗口停止新媒体写入后再做最终同步。

维护窗口方式：

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose stop polaris-wvp polaris-media
```

完成数据库备份和录像最终同步后：

```bash
docker compose start polaris-media polaris-wvp
docker compose ps
```

这会中断注册调度和媒体业务，必须提前通知并验证设备重新上线。

## 9. Redis 数据

Redis 当前启用了 AOF 并挂载 `volumes/redis/data/`。它包含缓存、票据或短期状态，不能代替 MySQL 业务备份。

需要完整保存 Redis 时，在维护窗口停止依赖写入和 Redis 后复制目录：

```bash
docker compose stop polaris-wvp polaris-redis
tar -czf "$BACKUP_DIR/redis-data.tar.gz" volumes/redis/data
docker compose start polaris-redis polaris-wvp
```

恢复旧 Redis 会让短期票据/缓存状态回到过去。多数灾备场景更适合让应用重建缓存并让用户重新登录，具体取决于业务连续性要求。

## 10. 备份完成检查

```bash
find "$BACKUP_DIR" -maxdepth 1 -type f -ls
cd "$BACKUP_DIR"
sha256sum -c SHA256SUMS
```

还要回答：

- 备份是否已经离开原服务器/原磁盘；
- 备份是否加密；
- 谁能解密；
- 密钥和备份是否分开保管；
- 保留周期和清理审批是什么；
- 最近一次恢复演练何时成功。

只创建文件、不做恢复演练的备份不算可靠。

## 11. 非破坏性恢复演练

不要直接覆盖生产 `wvp` 库。先在同一 MySQL 实例创建临时验证库，或更好地在隔离服务器/隔离 Compose 环境恢复。

### 11.1 解压备份副本

```bash
BACKUP_TIME='REPLACE_WITH_BACKUP_DIRECTORY_NAME'
gunzip -c "/srv/backups/wvp/$BACKUP_TIME/wvp.sql.gz" \
  > /srv/backups/wvp/restore-verify.sql
chmod 600 /srv/backups/wvp/restore-verify.sql
```

先把 `REPLACE_WITH_BACKUP_DIRECTORY_NAME` 替换为实际日期目录名。

### 11.2 创建验证库

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose exec -T polaris-mysql sh -c \
  'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" \
  -e "CREATE DATABASE wvp_restore_verify CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci"'
```

如果提示数据库已存在，先确认它是不是上一次仍需保留的演练证据，不要盲目 drop。

### 11.3 导入验证库

```bash
docker compose exec -T polaris-mysql sh -c \
  'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" wvp_restore_verify' \
  < /srv/backups/wvp/restore-verify.sql
```

### 11.4 核验关键数据

```bash
docker compose exec -T polaris-mysql sh -c \
  'exec mysql --user=root --password="$MYSQL_ROOT_PASSWORD" \
  -e "SELECT COUNT(*) AS table_count FROM information_schema.tables WHERE table_schema=\"wvp_restore_verify\"; \
      SELECT COUNT(*) AS user_count FROM wvp_restore_verify.wvp_user; \
      SELECT COUNT(*) AS device_count FROM wvp_restore_verify.wvp_device;"'
```

将表数、用户数、设备数与生产备份时记录对照。完整演练还要在隔离环境启动应用，验证登录、权限、设备/通道、录像元数据和文件。

演练库的删除属于破坏性操作。确认留档完成后，由数据库负责人在交互式 MySQL 中明确删除，不在本手册提供可被误复制到生产库的自动清理命令。

## 12. 生产恢复原则

真正恢复前必须：

1. 确认故障范围和恢复点；
2. 停止 WVP/媒体写入；
3. 再备份当前故障现场，保留调查证据；
4. 核验备份 SHA-256 和解密能力；
5. 在新空库或隔离实例恢复，不先覆盖原库；
6. 恢复与该数据库时间点匹配的配置、JWK、pepper 和录像；
7. 验证管理员与普通用户权限；
8. 验证设备注册、目录、点播和录像；
9. 经业务负责人确认后切换流量；
10. 保留旧环境直到回退窗口结束。

pepper 或 JWK 与数据库错配可能导致 API Key、Hook 或登录令牌失效；这就是为什么配置备份和数据库备份要属于同一恢复点。

## 13. 计划重启演练

Docker 已设置开机启动，容器有 restart policy，但仍要在维护窗口验证整机重启：

```bash
sudo reboot
```

重新连接后：

```bash
systemctl is-active docker
cd /opt/wvp-GB28181-pro/docker
docker compose ps
docker compose logs --since=15m polaris-wvp
```

再检查设备自动重新注册、目录仍在、点播正常、宿主机数据盘自动挂载。不要只检查页面能打开。

## 14. 更新镜像和代码

当前 Compose 有 `redis:latest`、`mysql:8`、`zlmediakit/zlmediakit:master` 等浮动标签。不要定时无条件 `pull` 后重启生产。

安全流程：

1. 读取发布说明和兼容性变化；
2. 完整备份；
3. 在测试环境用相同数据结构验证；
4. 把验证过的镜像同步到私有仓库并使用固定标签/摘要；
5. 记录旧镜像摘要；
6. 维护窗口更新；
7. 验证 Web、权限、设备、播放、录像；
8. 保留旧镜像和回滚配置。

中国大陆网络下，私有仓库还能避免回滚时才发现旧镜像已经无法从 Docker Hub/公共缓存获取。

## 15. 本章验收

- [ ] 有每日/每周状态和磁盘检查；
- [ ] 知道 restart、recreate、rebuild 和 down 的区别；
- [ ] Docker 日志与文件日志都有轮转/监控；
- [ ] MySQL 逻辑备份非空且校验通过；
- [ ] 配置、JWK、pepper 和 ZLM secret 已加密备份；
- [ ] 录像备份策略与数据库时间点协调；
- [ ] 备份已离开原服务器或原磁盘；
- [ ] 已在隔离/验证库完成恢复演练；
- [ ] 整机重启后设备和播放重新验证；
- [ ] 更新不依赖不可重现的浮动公共镜像。

[上一章：域名、HTTPS 与 NAT](07-domain-https-and-nat.md) · [返回首页](README.md) · [下一章：故障排查](09-troubleshooting.md)
