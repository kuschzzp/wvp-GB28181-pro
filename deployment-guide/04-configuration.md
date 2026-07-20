# 04 配置说明

[上一章：网络与端口](03-network-and-ports.md) · [返回首页](README.md) · [下一章：首次部署](05-first-deployment.md)

本章只修改配置，不启动项目。真正被 Compose 自动读取的是项目中的 [`docker/.env`](../docker/.env)，不是 `deployment-guide/examples/` 下的示例。

## 1. 配置前先备份

执行目录：Compose 目录。

```bash
cd /opt/wvp-GB28181-pro/docker
cp -a .env ".env.before-deploy.$(date +%Y%m%d-%H%M%S)"
chmod 600 .env
```

如果项目路径不同就替换。备份文件同样包含密钥，不能提交到公开仓库或随意发给别人。

查看当前非空配置项时可以使用：

```bash
sed -n '/^[[:space:]]*#/d; /^[[:space:]]*$/d; p' .env
```

输出会包含密码或密钥；只在自己的终端查看，不要直接粘贴到聊天或工单。

## 2. 三类地址的区别

当前 `.env` 有三个看似相同、用途不同的地址：

| 变量 | 谁使用这个地址 | 主要用途 |
|---|---|---|
| `Stream_IP` | 浏览器/外部调用者 | WVP 生成播放地址时公布的媒体入口地址 |
| `SDP_IP` | GB28181 设备 | SIP/SDP 中告诉设备 RTP 应发到哪里 |
| `SIP_ShowIP` | GB28181 设备、页面和平台内部信息 | Docker 环境中作为 WVP 对外使用的 SIP 本地地址，同时用于展示 |

局域网单机部署时三者通常都是服务器内网 IP。公网或 NAT 部署时，`SDP_IP` 和 `SIP_ShowIP` 通常是外部设备可达的公网 IP；`Stream_IP` 则必须是浏览器能够访问的地址。

不要填：

- `127.0.0.1`，除非所有访问者都和目标服务处于同一个网络命名空间；
- `0.0.0.0`，它是监听通配符，不是可访问目的地址；
- 设备无法解析或无法路由到的内部容器名，如 `polaris-media`；
- 只在你办公电脑上能解析的本地域名。

## 3. `.env` 每个变量逐项说明

### 3.1 `MediaRtmp`

当前值：`10001`。

ZLMediaKit 对外接收 RTMP 的宿主机端口。纯 GB28181 接入不依赖它；编码器或第三方系统向平台推 RTMP 时才需要。

如果修改，必须同时修改 [`docker/media/config.ini`](../docker/media/config.ini) 的 `[rtmp] port`。只改 `.env` 会让 Docker 映射的新端口与 ZLMediaKit 实际监听端口不一致。

常见错误：端口被占用；云安全组只开 TCP 但设备使用了别的方式；推流地址仍写旧端口。

### 3.2 `MediaRtsp`

当前值：`10002`。

ZLMediaKit 的 RTSP 接入端口。若平台仅主动拉取摄像机自己的 RTSP 地址，这个端口是否对公网开放取决于实际链路；不要无条件开放。

修改时同步修改 `docker/media/config.ini` 的 `[rtsp] port`。

### 3.3 `MediaRtp`

当前值：`10003`。

当前单端口模式下，GB28181 设备向 ZLMediaKit 发送 RTP 的端口。它同时在 Compose 中发布 TCP 和 UDP。

修改时同步修改 `docker/media/config.ini` 的 `[rtp_proxy] port`，还要修改防火墙、安全组、路由器映射和设备/SDP 相关验证口径。

症状：设备注册和目录都正常，点播后服务器 `10003` 没有 RTP 包或 ZLMediaKit 收不到流。

### 3.4 `API_KEY_PEPPER`

必填，至少 32 字节随机值。它用于保护平台 API Key 的摘要，不是设备 SIP 密码，也不是 JWT 本身。

要求：

- 与 `MEDIA_HOOK_SECRET_PEPPER` 使用不同随机值；
- 长期保管并纳入安全备份；
- 不提交到 Git；
- 已签发 API Key 后不要无计划替换，否则既有 Key 的校验可能失效。

Compose 使用 `${API_KEY_PEPPER:?...}` 强制检查。留空时 `docker compose config --quiet` 就应失败，这是安全保护，不是 Compose 故障。

### 3.5 `MEDIA_HOOK_SECRET_PEPPER`

必填，至少 32 字节随机值。用于媒体节点 Hook 身份和相关密钥派生/保护。

它必须与 API Key pepper 不同，并与配置、数据库和备份一起长期保管。替换时要计划重建 WVP、重新配置媒体节点并验证 Hook，而不是在线随手改一行。

### 3.6 `WebHttp`

当前值：`8080`。

这是宿主机对外的 HTTP 端口。Compose 实际映射为：

```text
宿主机 WebHttp -> polaris-nginx:8080
```

修改后访问地址、防火墙、安全组、NAT 和外层反向代理都要同步。Nginx 容器内部仍监听 8080，不要去把模板中的内部端口也机械改成同样值。

### 3.7 `WebHttps`

当前值：`8081`，但当前 Compose 没有使用它发布 HTTPS，WVP 的 `MediaHttps` 也被注释。

所以它现在只是预留配置。设置一个值不会自动生成证书，也不会让 `https://服务器:8081` 可用。

这里还有一个容易误判的细节：当前 ZLMediaKit 播放地址使用的 HTTPS 端口来自 [`docker/media/config.ini`](../docker/media/config.ini) 的 `[http] sslport`，ZLMediaKit 上线后由 WVP 读取；当前值是 `443`。`docker-compose.yml` 中被注释的 `MediaHttps` 会传给 `media.flv-ssl-port`/`media.ws-flv-ssl-port`，但当前 ZLMediaKit 地址生成路径并不使用这两个配置。因此不要把“取消 `MediaHttps` 注释”当成启用 HTTPS 的办法。

正式部署请保留 ZLMediaKit 上报的 `sslport=443`，并按 [07 域名、HTTPS 与 NAT](07-domain-https-and-nat.md) 在宿主机、云负载均衡或统一网关上提供真实的 443 TLS 入口。

### 3.8 `Stream_IP`

WVP 返回播放地址时使用的外部地址。它需要从“观看者”的网络视角可达。

- 正式 TLS：填写证书覆盖的域名，并确保观看端把它解析到 TLS 入口；
- 可信局域网 HTTP 实验：填写服务器内网 IP，同时按第 07 章设置 `WebHttp=80` 和 ZLMediaKit `sslport=0`；
- 直接公网/NAT：浏览器播放仍优先使用域名；公网 IP 主要填写到 `SDP_IP`、`SIP_ShowIP`，供设备的 SIP/RTP 链路使用；
- 首次只验证管理页面、SIP 和 RTP 时可以先用明确 IPv4，但在证书与域名未完成前不要把浏览器播放列为已验收。

症状：点播 API 成功、ZLMediaKit 也有流，但浏览器拿到的链接指向 `127.0.0.1` 或不可达内网地址。

### 3.9 `SDP_IP`

WVP 在 GB28181 点播的 SDP 中公布的媒体接收地址。摄像机必须能把 RTP 发到这个地址的 `MediaRtp` 端口。

- 同局域网：服务器内网 IP；
- 跨公网/NAT：外部设备可达的公网 IP，并保证端口转发到服务器；
- 不要写 Nginx 域名就假设 RTP 会通过 HTTPS 代理，RTP 是另一条链路。

症状：SIP INVITE/200 OK 正常，设备显示正在推流，但服务器抓不到 RTP；抓 SIP 包可看到 SDP 地址错误。

### 3.10 `SIP_ShowIP`

当前 Docker 配置中，WVP 实际监听 `0.0.0.0`，而 `SIP_ShowIP` 作为 Docker 环境对外使用的 SIP 本地地址，并在页面显示。它应是摄像机可访问的平台地址。

症状：设备的 REGISTER 能到服务器，但平台后续发出的 Catalog、INVITE 或响应消息中地址不可达；NAT 环境尤其明显。

### 3.11 `SIP_Port`

当前值：`8160`。WVP 同时发布 TCP 和 UDP。

修改后要同步：

- 摄像机的 SIP 服务器端口；
- Linux 防火墙；
- 云安全组；
- NAT 端口映射；
- 运维监控和抓包命令。

Compose 和 WVP 配置都从该变量读取，所以项目内部通常不需要再改另一处。

### 3.12 `SIP_Domain`

GB28181 域/Realm，通常为 10 位数字。设备页面可能叫“域”“SIP 域”“Realm”或“服务器域”。它不是互联网 DNS 域名。

常用规则是平台 20 位国标 ID 的前 10 位与域规划保持一致，但应按你的行政区划、行业编码和现有国标体系统一设计。不要让多套互联平台随意使用冲突的域。

症状：设备能发 REGISTER，但摘要认证反复 401、平台认为设备不属于该域，或设备界面提示注册失败。

### 3.13 `SIP_Id`

平台的 20 位 GB28181 编码。它是“平台自己”的 ID，不是摄像机设备 ID。每台接入设备还要有自己的 20 位 ID，并在平台中保持唯一。

大华、水星等不同品牌可能把它叫“服务器 ID”“平台编码”“SIP 服务器编号”。

### 3.14 `SIP_Password`

平台用于验证设备注册的默认认证密码。设备 GB28181 配置中的认证密码必须与之匹配，除非后续为设备配置独立密码。

建议用足够长但兼容设备输入限制的随机字母数字组合。部分旧固件对特殊字符、长度或复制粘贴支持不好，先在设备说明书中确认。

不要与管理员密码、数据库密码或 pepper 共用。

### 3.15 `RecordSip`

控制 GB28181 点播相关媒体是否进行云端录像。当前仓库默认 `.env` 为 `true`，录像目录映射到 `docker/volumes/video/`。

新手第一次打通时，如果尚未规划录像磁盘，可以先设为 `false`，确认存储和保留策略后再启用。启用后持续监控磁盘，不要认为停止浏览器播放一定立即释放所有历史录像。

### 3.16 `RecordPushLive`

控制推流直播类媒体是否录制。当前 `.env` 留空，应用配置的默认值为 `false`。建议明确写成 `false` 或 `true`，避免交接时不知道空值代表什么。

只使用 GB28181 时，它和 `RecordSip` 不是同一个开关。

## 4. 生成两个独立 pepper

执行目录：Compose 目录或安全运维终端。

先限制新文件默认权限：

```bash
umask 077
```

分别执行两次：

```bash
openssl rand -hex 32
openssl rand -hex 32
```

每次输出 64 个十六进制字符，即 32 字节。第一条填 `API_KEY_PEPPER`，第二条填 `MEDIA_HOOK_SECRET_PEPPER`。不要复用同一条输出。

推荐在密码管理器或受控密钥系统中记录：用途、部署环境、创建日期和保管人。不要在普通部署表中写明文，只写“保存在何处”。

## 5. 选择一个示例

三个示例文件都故意包含不能直接部署的占位符：

- [局域网示例](examples/env-lan.example)，使用第 07 章的 HTTP 80 实验模式，必须同时把 ZLMediaKit `[http] sslport` 改为 0；
- [直接公网示例](examples/env-public-ip.example)；
- [NAT 示例](examples/env-nat.example)。

可以对照编辑，不建议直接覆盖 `.env`，因为你必须逐项理解并替换标记：

```bash
cd /opt/wvp-GB28181-pro/docker
diff -u ../deployment-guide/examples/env-lan.example .env
```

`diff` 返回差异是正常现象。不要把 `REPLACE_WITH_...` 原样留在真实配置。

## 6. `.env` 之外必须同步处理的秘密

### 6.1 MySQL 密码

当前 [`docker/docker-compose.yml`](../docker/docker-compose.yml) 中存在：

- `MYSQL_ROOT_PASSWORD`；
- `MYSQL_PASSWORD`；
- WVP 的 `DATABASE_PASSWORD`。

全新部署应在第一次初始化 MySQL 前修改。`MYSQL_PASSWORD` 与 `DATABASE_PASSWORD` 必须相同；`MYSQL_ROOT_PASSWORD` 应使用另一个独立值。用户名 `wvp_user` 也同时出现在 MySQL 和 WVP 环境中，若改名，两处都要改。

可分别生成十六进制密码：

```bash
openssl rand -hex 24
openssl rand -hex 24
```

在 Compose YAML 中给密码加引号，并执行静态校验。不要把真实值写进本手册或提交到公开仓库。

MySQL 官方镜像只在空数据目录首次创建用户。数据库已经初始化后，仅修改 Compose 环境变量不会自动修改数据库内部密码。

### 6.2 ZLMediaKit API secret

当前固定值同时存在于：

- `docker/docker-compose.yml` 的 `ZLM_SERCERT`；
- `docker/media/config.ini` 的 `[api] secret`。

全新生产部署应生成新的随机值并同时替换两处：

```bash
openssl rand -hex 32
```

两处不一致时，WVP 无法通过 ZLMediaKit API 自动配置/控制媒体服务器，常见表现是媒体节点离线、调用 API 鉴权失败或点播无法继续。

### 6.3 JWK 文件

WVP 的 JWK 文件位于持久挂载的 `docker/wvp/config/jwk.json`。首次运行会按应用逻辑使用该位置。它关系到令牌验证，生成后必须与配置一起备份；不要把不同环境的 JWK 随意互换。

## 7. 端口变更的联动清单

| 改动 | 还要同步哪里 |
|---|---|
| `WebHttp` | 访问地址、防火墙、安全组、NAT、外层代理 |
| `SIP_Port` | 设备、防火墙、安全组、NAT |
| `MediaRtp` | `media/config.ini`、防火墙、安全组、NAT、抓包口径 |
| `MediaRtsp` | `media/config.ini`、RTSP 客户端地址、按需开放规则 |
| `MediaRtmp` | `media/config.ini`、推流地址、按需开放规则 |

最安全的首次部署方式是先使用当前端口，把网络打通后再有计划地统一改端口。

## 8. Compose 静态校验

编辑完成后，从 Compose 目录执行：

```bash
cd /opt/wvp-GB28181-pro/docker
docker compose config --quiet
```

成功时没有输出，退出码为 0。确认退出码：

```bash
echo $?
```

预期输出 `0`。

若 pepper 留空，应该看到类似“must be at least 32 bytes”的变量错误。先补齐变量，不要删除 Compose 中的必填检查。

查看最终镜像引用：

```bash
docker compose config --images
```

不要把完整的 `docker compose config` 输出发给别人，它可能展开数据库密码、SIP 密码和 pepper。

## 9. 授权模式不是 `.env` 变量

当前 [`docker/wvp/wvp/application-docker.yml`](../docker/wvp/wvp/application-docker.yml) 使用：

```yaml
authorization:
  mode: audit
```

`audit` 会计算并记录真实的允许/拒绝结果，但对受灰度模式控制的旧业务兼容放行；用户、角色、菜单、部门和视频资源组等授权配置接口仍有强制权限保护。它适合首次配置权限并观察审计。

生产正式阻断需要切换为 `enforce`，但必须在完成角色/资源授权、检查审计拒绝、验证 401/403 和播放票据之后进行。不要为了“看起来更安全”在首次登录前直接切换，也不要长期把 audit 当作最终权限隔离状态。首次部署的切换检查见 [05 首次部署](05-first-deployment.md) 和 [10 验收清单](10-acceptance-checklist.md)。

## 10. 本章验收

- [ ] `.env` 已备份且权限已收紧；
- [ ] 三个 IP 都从对应访问者视角可达；
- [ ] 两个 pepper 独立、非空、至少 32 字节并已安全保管；
- [ ] SIP 域、平台 ID 和认证密码已有统一规划；
- [ ] 已明确是否启用两类录像；
- [ ] 如修改媒体端口，已同步 `media/config.ini`；
- [ ] MySQL root 密码和应用密码已在首次初始化前修改；
- [ ] ZLM API secret 已同时修改两处；
- [ ] `docker compose config --quiet` 返回 0；
- [ ] 我知道 `audit` 不是最终权限隔离模式。

[上一章：网络与端口](03-network-and-ports.md) · [返回首页](README.md) · [下一章：首次部署](05-first-deployment.md)
