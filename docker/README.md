# WVP 2.8 Docker 全新部署

本目录用于从空数据目录部署 WVP、MySQL、Redis、ZLMediaKit 和 Nginx。初始化文件全部位于 `docker/` 内部，Compose 不依赖仓库中的中文路径，也不需要手工执行数据库升级脚本。

## 部署前准备

1. 确认 `volumes/mysql/data` 不包含旧 MySQL 数据；MySQL 只会在空数据目录首次启动时执行初始化 SQL。
2. 根据现场网络修改 `.env` 中的流媒体、SIP 地址和端口。
3. 在 `.env` 中填写两个不同的、至少 32 字节的随机值：

```dotenv
API_KEY_PEPPER=<至少32字节随机值>
MEDIA_HOOK_SECRET_PEPPER=<另一个至少32字节随机值>
```

这两个值必须长期保存，不要提交到仓库。更换它们会使已经签发的 API Key 或媒体 Hook 凭证失效。

## 首次启动

在本目录执行：

```bash
docker compose up -d --build
```

首次创建空 MySQL 数据目录时，官方 MySQL entrypoint 按文件名依次执行：

1. `mysql/db/wvp.sql`：创建 WVP 业务基础结构；
2. `mysql/db/wvp-authorization.sql`：创建 2.8 RBAC、部门、视频资源组、API Key scope、审计和媒体安全结构；
3. `mysql/db/wvp-authorization-final.sql`：移除旧角色主表，切换到最终用户角色关系表并创建过渡视图。

三份文件分别挂载为容器内的 `01-wvp.sql`、`02-wvp-authorization.sql` 和 `03-wvp-authorization-final.sql`。初始化完成后，角色主表是 `wvp_role`，用户角色关系表是 `wvp_user_role`。

查看状态和日志：

```bash
docker compose ps
docker compose logs -f polaris-mysql polaris-wvp
```

需要重新构建容器镜像时执行：

```bash
docker compose up -d --build --force-recreate
```

## 安全默认值

- WVP 的 `18978` 端口只在 Docker 网络内暴露，不映射到宿主机。
- 媒体 Hook 由 `media-net` 内的媒体节点直连 WVP；公网 Nginx 拒绝 `/index/hook/**`。
- 授权初始模式为 `audit`，URL token、旧明文 API Key、旧角色双写和旧媒体地址兼容默认关闭。
- 完成用户、角色、部门和视频资源组配置并通过权限核对后，再将授权模式切换为 `enforce`。

## 使用边界

这套入口只适用于全新部署。已有 MySQL 数据目录不会重新执行 `/docker-entrypoint-initdb.d`，也不应直接套用本说明覆盖升级。

`build.sh` 用于按日期生成镜像标签并推送到指定镜像仓库；Windows 可在 Git Bash 中执行。
