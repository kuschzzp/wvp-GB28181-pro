# 02 服务器准备

[上一章：架构与基础概念](01-architecture-and-concepts.md) · [返回首页](README.md) · [下一章：网络与端口](03-network-and-ports.md)

这一章完成四件事：确认服务器够用、校准系统环境、安装 Docker Engine 与 Compose v2、为中国大陆网络选择可回退的下载路径。

本章外部资料核对日期为 **2026-07-16**。Docker 支持版本和云厂商镜像政策会变化，实际部署时应打开相应链接再确认一次。

## 1. 选择服务器和操作系统

### 1.1 建议的入门配置

用于学习和小规模验证，可以从以下规格起步：

- 4 vCPU；
- 8 GB 内存；
- 50 GB 以上系统盘，不含长期录像空间；
- x86_64/amd64 架构；
- 千兆网络或按实际总码率规划的稳定带宽；
- 一块独立数据盘用于录像和备份更理想。

这不是容量承诺。视频平台的主要消耗来自：同时在线和点播的路数、每路码率、是否录像、录像保留天数、是否转码、磁盘随机/顺序写入能力，以及公网出入带宽。

### 1.2 粗略计算录像空间

不考虑封装和文件系统开销时：

```text
每路每天 GB ≈ 码率 Mbps × 10.8
总空间 GB ≈ 码率 Mbps × 10.8 × 录像路数 × 保留天数
```

例如 20 路、每路平均 4 Mbps、保留 7 天：

```text
4 × 10.8 × 20 × 7 = 6048 GB
```

也就是约 6.05 TB（十进制），还要为码率波动、索引、日志、文件系统和安全余量预留空间。建议再留至少 10%～20% 的余量，并用设备实际码率复算。

### 1.3 操作系统选择

本手册建议：

- 企业兼容生态：Rocky Linux 9，命令按 CentOS/RHEL 体系执行；
- 希望完全跟随 Docker 官方 CentOS 页面：使用仍在维护的 CentOS Stream；
- 熟悉 Debian/Ubuntu：使用 Ubuntu 22.04 LTS 或 24.04 LTS。

Docker 官方 CentOS 安装页当前明确列出受维护的 CentOS Stream 9/10。Rocky Linux 通常兼容相同 RPM 包，但它不是该页面逐字列出的支持对象，因此部署后要实际验证。参考：[Docker Engine on CentOS](https://docs.docker.com/engine/install/centos/)。

## 2. 第一次登录后的静态检查

以下命令只读取系统信息，不启动项目。

### 2.1 识别系统和架构

执行目录：任意目录。

```bash
cat /etc/os-release
uname -m
```

正常情况下，第一条能看到发行版和版本，第二条建议为 `x86_64`。如果是 `aarch64`/`arm64`，不能直接假设所有当前镜像都支持；安装 Docker 后要逐个检查 `redis`、`mysql`、`zlmediakit/zlmediakit`、`ringcentral/jdk`、`ubuntu` 和 `nginx` 的目标架构清单。

### 2.2 查看 CPU、内存和磁盘

```bash
lscpu
free -h
df -hT
lsblk -f
```

重点检查：

- 可用内存是否明显低于预期；
- `/` 或 `/opt` 是否接近满盘；
- 录像盘是否已经格式化并挂载；
- 重启后挂载是否会通过 `/etc/fstab` 自动恢复。

不要把录像目录放到一个临时挂载点。磁盘没挂载时，Linux 可能悄悄把录像写入系统盘同名目录，最终导致系统盘耗尽。

### 2.3 校准时区和时间

GB28181 注册、JWT、日志排序和证书都依赖正确时间。

```bash
timedatectl status
sudo timedatectl set-timezone Asia/Shanghai
timedatectl status
```

正常结果应包含：

```text
Time zone: Asia/Shanghai (CST, +0800)
System clock synchronized: yes
```

如果未同步，CentOS/Rocky 常用 chrony：

```bash
sudo dnf install -y chrony
sudo systemctl enable --now chronyd
chronyc tracking
```

Ubuntu：

```bash
sudo apt update
sudo apt install -y chrony
sudo systemctl enable --now chrony
chronyc tracking
```

`chronyc tracking` 能返回参考源、偏移量和同步状态即可。不要手工把时钟改成一个近似值后忽略时间同步。

### 2.4 检查 DNS 和基础网络

```bash
getent hosts download.docker.com
getent hosts mirrors.tuna.tsinghua.edu.cn
getent hosts mirrors.ustc.edu.cn
getent hosts maven.aliyun.com
```

返回 IP 表示 DNS 能解析；不表示 HTTPS 一定可用。如果所有域名都不能解析，先修服务器 DNS，不要靠不断更换镜像地址碰运气。

### 2.5 检查端口占用

```bash
sudo ss -lntup
```

先搜索当前计划使用的 `8080`、`8160`、`10001`、`10002`、`10003`。如果已有其他程序监听，后面要么停止冲突程序，要么统一修改 `.env`、防火墙、设备和端口映射，不能只改一处。

## 3. 中国大陆下载：先分清五条链路

这是本章最重要的一节。

| 链路 | 当前项目中的例子 | 加速位置 |
|---|---|---|
| 宿主机系统包 | `docker-ce`、Compose 插件、Git | dnf/apt 软件仓库 |
| Docker Hub 镜像 | `mysql:8`、`redis:latest` 等 | Docker daemon 加速器或自己的私有仓库 |
| 镜像构建内 apt | 容器里安装 Maven、Node.js、npm | Dockerfile 中 Ubuntu/Debian 软件源 |
| Maven | WVP Java 依赖 | `pom.xml`/Maven settings |
| npm | 前端依赖 | Nginx Dockerfile 的 npm registry |

### 3.0 本项目的中国大陆下载速查表

下面是当前可核验的候选地址。它们解决的层级不同，不要把任意一个地址复制到所有配置里。

| 下载对象 | 建议候选 | 当前项目如何使用 |
|---|---|---|
| Rocky Linux 基础包 | `https://mirrors.ustc.edu.cn/rocky/` | 只改宿主机 Rocky repo |
| CentOS Stream 基础包 | `https://mirrors.tuna.tsinghua.edu.cn/centos-stream/` | 只改宿主机 CentOS Stream repo |
| Ubuntu 基础包 | `https://mirrors.ustc.edu.cn/ubuntu/` 或 `https://mirrors.tuna.tsinghua.edu.cn/ubuntu/` | 只改宿主机 Ubuntu archive；ARM 另用 ubuntu-ports |
| Docker CE 软件包 | `https://mirrors.tuna.tsinghua.edu.cn/docker-ce/` 或 `https://mirrors.ustc.edu.cn/docker-ce/` | 安装 Docker Engine、Buildx、Compose 插件 |
| Docker Hub 容器镜像 | 账号专属加速地址；生产使用自己的 ACR/TCR/SWR | 拉取/保存 MySQL、Redis、ZLMediaKit 和构建基础镜像 |
| 容器内 Debian apt | `https://mirrors.tuna.tsinghua.edu.cn/debian/` 或 `https://mirrors.aliyun.com/debian/` | WVP 镜像构建时安装 Maven |
| 容器内 Ubuntu apt | `https://mirrors.tuna.tsinghua.edu.cn/ubuntu/` 或 `https://mirrors.aliyun.com/ubuntu/` | Nginx 镜像构建时安装 Node.js/npm |
| Maven | `https://maven.aliyun.com/repository/public` | 当前 `pom.xml` 已声明；ECC 独立仓库仍可能直连 |
| npm | `https://registry.npmmirror.com` | 当前 Nginx Dockerfile 已显式使用 |

部署服务器应自己测试 HTTPS，而不是以作者所在地的结果代替你的线路：

```bash
curl -fsSIL --connect-timeout 10 https://mirrors.tuna.tsinghua.edu.cn/docker-ce/ -o /dev/null
curl -fsSIL --connect-timeout 10 https://mirrors.ustc.edu.cn/docker-ce/ -o /dev/null
curl -fsSIL --connect-timeout 10 https://maven.aliyun.com/ -o /dev/null
curl -fsSIL --connect-timeout 10 https://registry.npmmirror.com -o /dev/null
```

命令退出码为 0 只说明此刻能建立并完成 HTTP 请求，不保证目标软件包、镜像标签或某个依赖一定存在。某个候选失败时先换同层级候选，不要把 Docker CE 软件包地址误填进 Docker Hub `registry-mirrors`。

清华 TUNA 和中科大 USTC 的 Docker CE 镜像只解决 Docker CE 软件包这一条，不解决 Docker Hub 拉镜像。TUNA 页面明确写着“本镜像不是 dockerhub”。参考：[TUNA Docker CE 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/docker-ce/) 和 [USTC Docker CE 帮助](https://mirrors.ustc.edu.cn/help/docker-ce.html)。

过去常见的 USTC Docker Hub 缓存已经关闭，不能再把 `docker.mirrors.ustc.edu.cn` 写进新部署。参考：[USTC Docker Hub 服务状态](https://mirrors.ustc.edu.cn/help/dockerhub.html)。

### 3.1 宿主机基础软件源也要单独判断

云厂商提供的 CentOS/Rocky/Ubuntu 镜像通常已经配置了境内软件源。`dnf makecache` 或 `apt update` 速度正常、签名检查正常时，不要为了“统一”再改一次。

如果宿主机基础源确实不可用，先备份，再按发行版和版本选择镜像站官方步骤：

- Rocky Linux 9/10：[USTC Rocky Linux 帮助](https://mirrors.ustc.edu.cn/help/rocky.html)；
- CentOS Stream 9：[TUNA CentOS Stream 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/centos-stream/)；
- Ubuntu x86_64：[TUNA Ubuntu 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/ubuntu/) 或 [USTC Ubuntu 帮助](https://mirrors.ustc.edu.cn/help/ubuntu.html)；
- Ubuntu ARM 等架构：[USTC Ubuntu Ports 帮助](https://mirrors.ustc.edu.cn/help/ubuntu-ports.html)。

Rocky Linux 9/10 当前 USTC 官方示例只修改默认启用的两个 repo 文件：

```bash
sudo cp -a /etc/yum.repos.d/rocky.repo /etc/yum.repos.d/rocky.repo.before-cn-mirror
sudo cp -a /etc/yum.repos.d/rocky-extras.repo /etc/yum.repos.d/rocky-extras.repo.before-cn-mirror
sudo sed -e 's|^mirrorlist=|#mirrorlist=|g' \
  -e 's|^#baseurl=http://dl.rockylinux.org/$contentdir|baseurl=https://mirrors.ustc.edu.cn/rocky|g' \
  -i \
  /etc/yum.repos.d/rocky.repo \
  /etc/yum.repos.d/rocky-extras.repo
sudo dnf clean all
sudo dnf makecache
```

文件名不存在时不要创建空文件硬套命令，先重新确认 Rocky 版本和官方帮助页。回退时复制 `.before-cn-mirror` 备份并重建缓存。

Ubuntu 24.04 默认使用 `/etc/apt/sources.list.d/ubuntu.sources`，22.04 常用 `/etc/apt/sources.list`。以 24.04 x86_64 为例，只替换 archive 源并暂时保留官方 security 源的及时性：

```bash
sudo cp -a /etc/apt/sources.list.d/ubuntu.sources \
  /etc/apt/sources.list.d/ubuntu.sources.before-cn-mirror
sudo sed -i 's@//.*archive.ubuntu.com@//mirrors.ustc.edu.cn@g' \
  /etc/apt/sources.list.d/ubuntu.sources
sudo apt update
```

如果使用云厂商区域 archive 域名、ARM `ports.ubuntu.com` 或不同 Ubuntu 版本，必须在镜像站页面选择对应格式/架构，不能照抄 x86_64 例子。镜像站同步可能有延迟，尤其安全更新要结合组织补丁策略；不能通过关闭签名验证解决源错误。

宿主机源、Docker CE 源和 Docker Hub 仍是三套配置。改好 Ubuntu/Rocky 基础源不会自动改变 Dockerfile 中容器内部的 apt 源。

## 4. CentOS Stream / Rocky Linux 安装 Docker

以下给两个路径，只选一个。建议中国大陆服务器先用路径 B；若你所在网络直接访问 Docker 官方仓库稳定，可用路径 A。

### 4.1 先安装工具

```bash
sudo dnf -y install dnf-plugins-core git curl ca-certificates openssl
```

正常结果是所有包已安装或成功安装，没有 unresolved dependency。

### 4.2 路径 A：Docker 官方仓库

依据：[Docker Engine on CentOS](https://docs.docker.com/engine/install/centos/)。

```bash
sudo dnf config-manager --add-repo https://download.docker.com/linux/centos/docker-ce.repo
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

如果下载 repo 文件或 RPM 长时间超时，停止重复尝试，改用路径 B。

### 4.3 路径 B：中国大陆 Docker CE 软件包镜像

以下以清华 TUNA 为主。它提供 Docker 官方软件包的镜像，不是匿名 Docker Hub 代理。

```bash
sudo curl -fsSL https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/centos/docker-ce.repo \
  -o /etc/yum.repos.d/docker-ce.repo
sudo cp -a /etc/yum.repos.d/docker-ce.repo /etc/yum.repos.d/docker-ce.repo.before-cn-mirror
sudo sed -i 's+https://download.docker.com+https://mirrors.tuna.tsinghua.edu.cn/docker-ce+g' \
  /etc/yum.repos.d/docker-ce.repo
sudo dnf clean all
sudo dnf makecache
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

第一条如果因线路问题失败，可选 USTC，但不要同时混用两个 base URL：

```bash
sudo curl -fsSL https://mirrors.ustc.edu.cn/docker-ce/linux/centos/docker-ce.repo \
  -o /etc/yum.repos.d/docker-ce.repo
sudo cp -a /etc/yum.repos.d/docker-ce.repo /etc/yum.repos.d/docker-ce.repo.before-cn-mirror
sudo sed -i 's+https://download.docker.com+https://mirrors.ustc.edu.cn/docker-ce+g' \
  /etc/yum.repos.d/docker-ce.repo
sudo dnf clean all
sudo dnf makecache
sudo dnf install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

检查仓库实际地址：

```bash
grep -E '^(baseurl|metalink|mirrorlist)=' /etc/yum.repos.d/docker-ce.repo
```

输出应与所选 TUNA 或 USTC 路径一致，不应出现两个来源交错。

恢复官方地址时，优先恢复备份：

```bash
sudo cp -a /etc/yum.repos.d/docker-ce.repo.before-cn-mirror /etc/yum.repos.d/docker-ce.repo
sudo dnf clean all
sudo dnf makecache
```

### 4.4 启动并验证 Docker

```bash
sudo systemctl enable --now docker
sudo systemctl status docker --no-pager
sudo docker version
sudo docker compose version
```

正常结果：

- `systemctl` 显示 `active (running)`；
- `docker version` 同时显示 Client 和 Server；
- `docker compose version` 显示 v2 版本。

不强制执行 `docker run hello-world`。它会额外访问 Docker Hub；在容器镜像加速还没配置好时，下载失败不能证明 Docker Engine 安装失败。

## 5. Ubuntu 安装 Docker

下面适用于当前 Docker 官方支持的 Ubuntu 版本。先看 [Docker Engine on Ubuntu](https://docs.docker.com/engine/install/ubuntu/)，确认你的版本仍受支持。

### 5.1 安装基础工具

```bash
sudo apt update
sudo apt install -y ca-certificates curl git openssl
sudo install -m 0755 -d /etc/apt/keyrings
```

### 5.2 路径 A：Docker 官方 apt 仓库

```bash
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
```

创建源文件：

```bash
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://download.docker.com/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
```

这里的 `$(...)` 会读取当前服务器的 Ubuntu 代号和 CPU 架构，不是要手填的文本。

### 5.3 路径 B：TUNA Docker CE apt 镜像

仍使用 Docker 的签名密钥，只把软件包仓库换成 TUNA：

```bash
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg \
  -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
sudo tee /etc/apt/sources.list.d/docker.sources >/dev/null <<EOF
Types: deb
URIs: https://mirrors.tuna.tsinghua.edu.cn/docker-ce/linux/ubuntu
Suites: $(. /etc/os-release && echo "${UBUNTU_CODENAME:-$VERSION_CODENAME}")
Components: stable
Architectures: $(dpkg --print-architecture)
Signed-By: /etc/apt/keyrings/docker.asc
EOF
```

若 Docker 官方 GPG 地址本身无法访问，TUNA 镜像也提供对应路径。使用前打开 [TUNA Docker CE 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/docker-ce/) 核对当前说明，不要从论坛下载密钥。

保存你最终使用的源文件：

```bash
sudo cp -a /etc/apt/sources.list.d/docker.sources \
  /etc/apt/sources.list.d/docker.sources.working-copy
```

### 5.4 安装、启动和验证

```bash
sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo systemctl enable --now docker
sudo systemctl status docker --no-pager
sudo docker version
sudo docker compose version
```

如果 `apt update` 报 `NO_PUBKEY` 或 `not signed`，不要加 `[trusted=yes]` 绕过签名。重新检查 `Signed-By` 路径、密钥文件是否为空和系统时间。

## 6. 是否把普通用户加入 docker 组

默认使用 `sudo docker ...` 最清晰。也可以把当前用户加入 `docker` 组：

```bash
sudo usermod -aG docker "$(id -un)"
```

然后退出 SSH 并重新登录，再执行：

```bash
docker version
```

Docker 官方提醒：`docker` 组基本等同于宿主机 root 级能力。不要把不受信任的普通账号加入该组。参考：[Docker Linux post-installation](https://docs.docker.com/engine/install/linux-postinstall/)。

后续命令默认当前用户已有 Docker 权限；若没有，就在命令前加 `sudo`。

## 7. 配置 Docker Hub 镜像拉取路径

### 7.1 当前项目需要哪些镜像

首次部署需要拉取或作为构建基础使用：

| 原始引用 | 来源文件 | 用途 |
|---|---|---|
| `redis:latest` | `docker-compose.yml` | Redis |
| `mysql:8` | `docker-compose.yml` | MySQL |
| `zlmediakit/zlmediakit:master` | `docker-compose.yml` | ZLMediaKit |
| `ringcentral/jdk:21.0.9` | WVP Dockerfile | Java 构建和运行基础镜像 |
| `ubuntu:24.04` | Nginx Dockerfile | 前端构建环境 |
| `nginx:alpine` | Nginx Dockerfile | Web 网关运行镜像 |

其中 `latest` 和 `master` 是浮动标签，未来可能指向不同内容；某些国内缓存也可能只保留旧版本。因此“拉取成功”还要结合镜像摘要和实际测试，不能只看标签名字。

### 7.2 临时学习环境：账号专属加速器

如果你使用阿里云、腾讯云或华为云，先查看本账号控制台给出的镜像服务方案，不要照抄别人的专属域名。

阿里云 ACR 当前明确提示：账号加速器已经停止同步最新镜像，`latest` 可能不是最新，并且生产环境应谨慎依赖 Docker Hub。参考：[阿里云 ACR 官方镜像加速说明](https://help.aliyun.com/zh/acr/user-guide/accelerate-the-pulls-of-docker-official-images)。它可以作为个人验证的候选路径，但不是本手册的生产首选。

华为云也说明公共加速能力有云内、个人开发和镜像范围限制，生产环境推荐把镜像同步到自己的 SWR。参考：[华为云 SWR 调整公告](https://support.huaweicloud.com/bulletin-swr/swr_bulletin_0003.html)。

### 7.3 安全修改 `/etc/docker/daemon.json`

先检查文件是否已有其他配置：

```bash
sudo test -f /etc/docker/daemon.json && sudo cat /etc/docker/daemon.json
```

如果存在，先备份：

```bash
sudo cp -a /etc/docker/daemon.json /etc/docker/daemon.json.before-registry-mirror
```

如果原文件不存在：

```bash
sudo install -d -m 0755 /etc/docker
```

然后用 `sudoedit /etc/docker/daemon.json` 编辑。文件原来为空时可以使用下面结构；把占位符换成控制台为你账号生成的 HTTPS 地址：

```json
{
  "registry-mirrors": [
    "https://YOUR_ACCOUNT_SPECIFIC_MIRROR"
  ]
}
```

如果文件已有 `log-driver`、`data-root` 等字段，只新增 `registry-mirrors` 字段并保证 JSON 逗号正确，不要整文件覆盖。

验证 JSON 和 Docker 配置，再重启：

```bash
sudo dockerd --validate --config-file=/etc/docker/daemon.json
sudo systemctl restart docker
sudo systemctl status docker --no-pager
docker info
```

正常结果：第一条输出 `configuration OK`，`docker info` 的 `Registry Mirrors` 下能看到你的地址。

回退时，如果之前有备份：

```bash
sudo cp -a /etc/docker/daemon.json.before-registry-mirror /etc/docker/daemon.json
sudo dockerd --validate --config-file=/etc/docker/daemon.json
sudo systemctl restart docker
```

如果之前根本没有该文件，不要直接删除当前文件；先改名保留证据：

```bash
sudo mv /etc/docker/daemon.json /etc/docker/daemon.json.disabled
sudo systemctl restart docker
```

### 7.4 生产推荐：自己的 ACR/TCR/SWR 仓库

稳定方案不是长期追逐公共代理，而是把部署所需镜像复制到你控制的私有仓库，并记录摘要。阿里云 ACR、腾讯云 TCR、华为云 SWR 都属于这类产品。参考：[阿里云 ACR](https://help.aliyun.com/zh/acr/)、[腾讯云 TCR](https://cloud.tencent.com/document/product/1141/39278)、[华为云 SWR](https://support.huaweicloud.com/intl/en-us/usermanual-swr/swr_01_0045.html)。

通用步骤如下，实际登录域名和项目名由你的云账号提供：

```bash
REGISTRY_HOST='REPLACE_WITH_YOUR_REGISTRY_HOST'
REGISTRY_PROJECT='REPLACE_WITH_YOUR_REGISTRY_PROJECT'
docker login "$REGISTRY_HOST"
docker pull mysql:8
docker image inspect mysql:8 --format '{{json .RepoDigests}}'
docker tag mysql:8 "$REGISTRY_HOST/$REGISTRY_PROJECT/mysql:8-verified"
docker push "$REGISTRY_HOST/$REGISTRY_PROJECT/mysql:8-verified"
```

两项 `REPLACE_WITH_...` 必须先替换；保留占位符时命令应失败，不能把它当成真实仓库名继续部署。

对六个原始引用分别处理。然后要同步修改：

- `docker/docker-compose.yml` 中三个 `image:`；
- `docker/wvp/Dockerfile` 中两个 `FROM ringcentral/jdk:21.0.9`；
- `docker/nginx/Dockerfile` 中 `FROM ubuntu:24.04` 和 `FROM nginx:alpine`。

修改后从 `docker/` 执行：

```bash
docker compose config --images
```

检查输出中是否全是你预期的私有仓库地址。不要把私有仓库密码写进 Compose 文件，使用 `docker login` 的凭证机制，并限制服务器上 Docker 配置目录的权限。

## 8. 镜像构建内部的国内下载源

Docker Hub 镜像成功拉下来以后，两个 Dockerfile 还会联网安装依赖。

### 8.1 WVP 构建

`docker/wvp/Dockerfile` 会在 `ringcentral/jdk:21.0.9` 中通过 apt 安装 Maven，再运行：

```text
mvn clean package -Dmaven.test.skip=true
```

当前 `pom.xml` 已声明：

```text
https://maven.aliyun.com/repository/public
```

这是阿里云 Maven 公共代理仓库。参考：[阿里云 Maven 镜像](https://developer.aliyun.com/mirror/maven/)。但项目还明确声明了 `https://maven.ecc.no/releases`；阿里云公共仓库不保证替代这个独立仓库。因此 Maven 卡住时，要看报错 URL，不能只改 Docker Hub 镜像。

如果报错发生在 `apt-get update`，说明是容器内 Debian 软件源问题。当前 Dockerfile 已留有把 `deb.debian.org` 换为阿里云的注释行，但默认没有启用。修改前先确认基础镜像实际采用 `/etc/apt/sources.list.d/debian.sources`，再参照 [TUNA Debian 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/debian/) 或 [阿里云 Debian 镜像](https://developer.aliyun.com/mirror/debian) 处理；不要猜基础镜像代号。

### 8.2 Nginx/前端构建

`docker/nginx/Dockerfile` 使用 `ubuntu:24.04` 安装 Node.js 和 npm，然后执行：

```text
npm --registry=https://registry.npmmirror.com install
npm run build:prod
```

因此 npm 已走 `registry.npmmirror.com`，但前面的 Ubuntu `apt-get update` 仍可能受线路影响。Ubuntu 24.04 使用 DEB822 格式的 `/etc/apt/sources.list.d/ubuntu.sources`，国内替代方式应参考 [TUNA Ubuntu 帮助](https://mirrors.tuna.tsinghua.edu.cn/help/ubuntu/) 或 [阿里云 Ubuntu 镜像](https://developer.aliyun.com/mirror/ubuntu)。

在 Dockerfile 中切换 apt 源属于项目构建配置变更。先保存原文件，明确 CPU 架构和 Ubuntu/Debian 版本，再改；不要在宿主机改了 apt 源后误以为容器构建也会自动继承。

## 9. 安装后的最终检查

执行目录：任意目录。

```bash
docker version
docker compose version
docker buildx version
systemctl is-enabled docker
systemctl is-active docker
```

期望：三个版本命令正常，最后两条分别输出 `enabled` 和 `active`。

再检查项目目录的可写空间：

```bash
cd /opt/wvp-GB28181-pro
df -hT .
test -w docker && echo "docker 目录可写"
```

如果项目不在 `/opt/wvp-GB28181-pro`，替换为实际路径。

## 10. 这一章的验收

- [ ] 操作系统处于维护期，架构已确认；
- [ ] 时间为 `Asia/Shanghai` 且已同步；
- [ ] 系统盘、数据盘和录像空间已规划；
- [ ] Docker Engine、Buildx、Compose v2 已安装并启动；
- [ ] 我知道 Docker CE 包镜像不是 Docker Hub 镜像；
- [ ] 没有使用已经关闭的 USTC Docker Hub 缓存；
- [ ] 若配置账号加速器，已备份 `daemon.json` 并知道如何回退；
- [ ] 生产部署已计划使用自己的私有仓库或至少记录镜像摘要；
- [ ] 我知道 WVP 构建还会访问 apt、Maven 和 ECC 仓库；
- [ ] 我知道 Nginx 构建还会访问 Ubuntu apt 和 npm 镜像。

[上一章：架构与基础概念](01-architecture-and-concepts.md) · [返回首页](README.md) · [下一章：网络与端口](03-network-and-ports.md)
