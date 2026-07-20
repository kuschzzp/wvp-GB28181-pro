# WVP 2.8 Docker 全新部署设计

## 背景

当前 `docker-compose.yml` 把 `../数据库/2.8.0/更新-mysql-2.8.0.sql` 直接挂载进 MySQL。这个做法依赖中文宿主机路径，也把旧库升级流程带进了全新部署。它虽然能在空库上完成初始化，但不适合作为跨平台 Docker 发布入口。

本次只处理全新部署。已有数据库升级、旧版本回退和生产 contract 不进入 Docker 启动流程，仍保留在独立迁移手册中。

## 目标

- Compose 中不再出现中文文件或目录路径。
- `docker/` 目录自身包含新装所需的全部 MySQL 初始化 SQL。
- 空数据卷第一次启动后直接得到可供当前 2.8 应用使用的最终角色关系结构。
- Docker 默认关闭旧角色双写、明文旧 API Key 认证、URL token 和长期媒体地址兼容。
- 保留现有 Hook、下载票据、Nginx 内部文件转发和 WVP 内网端口保护。
- 使用真实 MySQL 空库契约证明初始化成功，测试结束后不遗留容器。

## 不在本次范围内

- 不处理已有 MySQL 数据卷。
- 不在 Docker 启动时探测或升级旧表。
- 不对已有生产数据库执行迁移或 contract。
- 不启动 WVP、ZLMediaKit、Redis 或 Nginx 业务服务进行联调。
- 不提交或推送 Git。

## 初始化结构

Docker 发布包在 `docker/mysql/db/` 下保留三份纯英文路径的 SQL：

1. `wvp.sql`：现有 WVP 业务基础表。
2. `wvp-authorization.sql`：2.8 用户、角色、部门、菜单、视频资源组、API Key scope、审计和媒体安全结构。
3. `wvp-authorization-final.sql`：把旧角色主表收缩为最终 `wvp_user_role` 关系表，并创建当前应用过渡期使用的可更新视图 `wvp_user_role_rel`。

Compose 分别把它们挂载为容器内的 `01-wvp.sql`、`02-wvp-authorization.sql`、`03-wvp-authorization-final.sql`。MySQL 官方 entrypoint 只在空数据目录首次启动时按文件名顺序执行。SQL 之间只引用容器内英文路径，不再读取仓库根目录的 `数据库/`。

Docker 本地 SQL 是新装入口的独立发布资产。迁移目录中的升级脚本继续服务旧库，但不再被 Compose 引用。两者通过结果契约校验，不依赖文件内容完全相同。

## 默认配置

JAR 内置的 Docker profile 与 `docker/wvp/wvp/` 下的外置配置统一设置：

```yaml
authorization:
  mode: audit
  allow-url-token-compatibility: false
  legacy-api-key-enabled: false
  legacy-role-dual-write-enabled: false
  media:
    legacy-response-enabled: false
```

初始仍使用 `audit`，便于首次配置角色、部门和视频资源组；这不影响登录认证、API Key 摘要校验、媒体 Hook 身份或下载票据保护。完成现场权限配置和 readiness 后，再由部署人员切换 `enforce`。

`API_KEY_PEPPER` 与 `MEDIA_HOOK_SECRET_PEPPER` 继续由 `.env` 强制提供。数据库账号密码本次不扩大重构范围，后续可单独做凭据参数化和镜像版本锁定。

## 验证

实施后执行以下非业务运行验证：

1. `docker compose config --quiet`，确认 Compose 可解析。
2. 静态扫描 Compose，确认不存在 `../数据库`、`/数据库/` 或其他中文挂载路径。
3. MySQL Testcontainers 空库测试依次执行三份 Docker SQL，验证最终表、视图、默认数据、索引、自增和角色/API Key 行为。
4. 验证 `legacy-role-dual-write-enabled=false` 在 JAR 内置 Docker profile 和外置 Docker 配置中一致。
5. 检查 Testcontainers 容器全部退出，删除测试日志和构建产物。

## 验收结果

- 新用户只需准备空 Docker 数据目录和 `.env`，不需要手工执行任何升级 SQL。
- `docker-compose.yml` 的所有宿主机挂载路径均为英文路径。
- 初始化完成后不存在旧角色主表；`wvp_user_role` 是最终关系表，`wvp_role` 是角色主表。
- Docker 默认不执行旧角色双写，也不启用任何旧凭证兼容开关。

## 实施状态（2026-07-16）

本设计已完成落地。Compose 现在依次挂载三份 Docker 本地 SQL，废弃的 Compose `version` 字段已经移除；JAR 内置 Docker profile 和外置 Docker 配置均关闭旧角色双写，`.env` 已增加两个必填 pepper 的空配置项。

验证结果：Compose 静态解析通过；MySQL 8.4 空库实际执行三份 Docker SQL 后得到最终角色关系结构；MySQL 旧库迁移、PostgreSQL 迁移和 H2 逻辑结构契约同时通过。验证只启动 Testcontainers 一次性数据库容器，没有启动 WVP、ZLMediaKit、Redis 或 Nginx 业务服务。
