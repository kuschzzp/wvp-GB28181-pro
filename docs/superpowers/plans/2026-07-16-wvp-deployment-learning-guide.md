# WVP Deployment Learning Guide Implementation Plan

> **For agentic workers:** Execute this plan task-by-task inline in the current task. The user requested no subagents and no Git commit or push.

**Goal:** Create a self-contained Chinese learning guide that lets a Docker and GB28181 beginner deploy the current WVP stack on one CentOS/Rocky or Ubuntu server, connect devices, operate it safely, and troubleshoot common failures.

**Architecture:** Place all learner-facing material under a new root-level `deployment-guide/` directory. Keep navigation, concepts, procedures, examples, operations, and troubleshooting in focused files; derive project-specific facts from the current Compose and configuration files, and verify changing system-installation instructions against primary official documentation.

**Tech Stack:** Markdown, Mermaid, Docker Engine, Docker Compose v2, CentOS/Rocky Linux, Ubuntu, WVP, ZLMediaKit, MySQL 8, Redis, Nginx, GB28181.

## Global Constraints

- Do not modify existing project README files for this learning guide.
- Do not start WVP, ZLMediaKit, MySQL, Redis, Nginx, or the Compose project.
- Do not create real credentials or include deployable fixed secrets in examples.
- Use CentOS/Rocky as the main path and show Ubuntu differences at the point where commands differ.
- Cover only a fresh single-server deployment; do not describe old-database version upgrades.
- Treat mainland-China download routing as five separate concerns: host operating-system packages, Docker CE packages, container images, container-build apt packages, and Maven/npm dependencies. Prefer institutional/official sources and always document service limitations and rollback.
- Do not recommend anonymous public Docker Hub proxies as the production default. Prefer an account-specific cloud accelerator for initial testing and a user-controlled ACR/TCR/SWR registry for repeatable production deployment.
- Never recommend disabling the firewall, permanently disabling SELinux, deleting MySQL data casually, or using unbounded `chmod 777`.
- Do not commit or push.

---

### Task 1: Establish the fact sheet and current official references

**Files:**
- Read: `docker/docker-compose.yml`
- Read: `docker/.env`
- Read: `docker/wvp/Dockerfile`
- Read: `docker/wvp/wvp/application-docker.yml`
- Read: `docker/media/config.ini`
- Read: `docker/nginx/templates/nginx.conf.template`
- Read: `docker/mysql/db/wvp.sql`
- Read: `docker/mysql/db/wvp-authorization.sql`
- Read: `docker/mysql/db/wvp-authorization-final.sql`

**Interfaces:**
- Consumes: Current repository deployment contract and primary operating-system/container documentation.
- Produces: A verified fact set used by every chapter: service names, ports, mounts, variables, credentials workflow, SQL order, login behavior, and source links.

- [x] **Step 1: Record the project-specific deployment contract**

Extract the five Compose services, externally published ports, internal-only ports, bind mounts, required variables, health checks, startup dependencies, and three SQL filenames. Confirm the initial administrator record and the raw-password-to-BCrypt upgrade path from SQL and authentication code.

- [x] **Step 2: Verify changing installation instructions against official sources**

Use current Docker documentation for CentOS and Ubuntu installation, Docker Linux post-installation guidance, Compose CLI behavior, and firewall caveats. Use official firewalld, Ubuntu, Nginx, and Let's Encrypt documentation only where exact external commands are included.

- [x] **Step 3: Verify mainland-China download alternatives and limitations**

Use the current TUNA and USTC Docker CE help pages for package mirrors, and explicitly record that these are not Docker Hub mirrors. Verify the current USTC Docker Hub shutdown notice and the current ACR accelerator freshness/usage warning. Describe TCR/SWR/ACR private registries as durable production options without inventing account-specific registry addresses. Inspect both Dockerfiles and `pom.xml` to determine which build-time repositories are actually used; include npm in the first-deployment critical path because the Nginx image runs `npm install` and `npm run build:prod`.

- [x] **Step 4: Write source-boundary rules into the guide**

Every externally changing installation section must include an official link and a “核对日期：2026-07-16” note. Project-specific behavior links to repository-relative files instead of claiming external guarantees.

### Task 2: Create navigation and architecture chapters

**Files:**
- Create: `deployment-guide/README.md`
- Create: `deployment-guide/01-architecture-and-concepts.md`

**Interfaces:**
- Consumes: Task 1 fact set.
- Produces: Stable chapter links, glossary, service map, and three Mermaid flows referenced by later chapters.

- [x] **Step 1: Create the guide navigation**

`README.md` must contain: scope, who should read it, prerequisites, recommended reading order, a shortest-path checklist, chapter table, safety warnings, and the rule that commands are run from either repository root or `docker/` as explicitly labelled.

- [x] **Step 2: Explain the five services**

Describe WVP as signaling/management/authentication, ZLMediaKit as media transport/protocol output, MySQL as persistent business data, Redis as cache/session coordination, and Nginx as the public Web/API/media gateway.

- [x] **Step 3: Add three Mermaid diagrams**

Include separate diagrams for browser management requests, GB28181 registration/catalog signaling, and device RTP media arriving at ZLMediaKit before browser playback. Diagrams must distinguish host-published ports from Docker-network-only ports.

- [x] **Step 4: Add a beginner glossary**

Define host, container, image, Compose project, bind mount, empty data directory, SIP, SDP, RTP, RTSP, RTMP, HTTP-FLV, NAT, public IP, private IP, security group, firewall, and SELinux in operational terms.

### Task 3: Write server preparation and network planning

**Files:**
- Create: `deployment-guide/02-server-preparation.md`
- Create: `deployment-guide/03-network-and-ports.md`

**Interfaces:**
- Consumes: Official source links and Compose port contract from Task 1.
- Produces: Installed Docker/Compose prerequisites and a completed network worksheet required by configuration examples.

- [x] **Step 1: Write capacity and operating-system preparation**

Explain conservative starting resources without promising channel capacity: 4 CPU cores, 8 GB RAM, and separate disk planning as a learning baseline; require the reader to calculate storage from bitrate, recording duration, and retention. Include architecture, disk, memory, time sync, timezone, DNS, and port-conflict checks.

- [x] **Step 2: Write CentOS/Rocky Docker installation**

Use the official Docker repository method, distinguish supported CentOS instructions from Rocky’s compatibility use, install Engine plus Compose plugin, enable Docker, and verify with `docker version` and `docker compose version`. Explain the risk of adding a user to the `docker` group.

- [x] **Step 3: Write Ubuntu Docker installation**

Use Docker’s apt repository and keyring method, then the same verification. Do not use convenience scripts as the main path.

- [x] **Step 4: Add the mainland-China source decision tree**

Give Docker's official repository as the baseline and TUNA/USTC as separately selectable Docker CE package mirrors. Cover host Rocky/CentOS Stream/Ubuntu mirrors separately from Docker CE. For Docker Hub image pulls, show how to back up and merge `/etc/docker/daemon.json`, obtain an account-specific accelerator, verify `docker info`, and restore the previous file. Warn that the current ACR accelerator may be stale and that USTC's former Docker Hub endpoint is closed. List every image required by the current Compose/Dockerfile so the reader can synchronize and retag them into a private registry. Explain container-build apt as another independent path, that the Maven build already declares Aliyun's public repository but still needs explicitly declared non-central repositories, and that the Nginx image's first build uses `registry.npmmirror.com` for npm.

- [x] **Step 5: Build the network worksheet and port table**

Cover LAN-only, direct public-IP, and router-NAT topologies. The port table must include `8080/tcp`, `8160/tcp+udp`, `10001/tcp+udp`, `10002/tcp+udp`, and `10003/tcp+udp` as current defaults, while explaining which are mandatory for GB28181 versus external RTSP/RTMP ingestion.

- [x] **Step 6: Explain three independent network controls**

Provide firewalld and UFW examples, cloud security-group guidance, and router port-forwarding checks. Explain SELinux labels for bind mounts without recommending global disablement.

### Task 4: Write configuration and safe examples

**Files:**
- Create: `deployment-guide/04-configuration.md`
- Create: `deployment-guide/examples/env-lan.example`
- Create: `deployment-guide/examples/env-public-ip.example`
- Create: `deployment-guide/examples/env-nat.example`

**Interfaces:**
- Consumes: Network worksheet from Task 3 and the exact current `docker/.env` variable set.
- Produces: Three non-active templates and an item-by-item configuration reference used by first deployment.

- [x] **Step 1: Explain every `.env` variable**

For `MediaRtmp`, `MediaRtsp`, `MediaRtp`, both peppers, `WebHttp`, `WebHttps`, `Stream_IP`, `SDP_IP`, `SIP_ShowIP`, `SIP_Port`, `SIP_Domain`, `SIP_Id`, `SIP_Password`, `RecordSip`, and `RecordPushLive`, state purpose, correct address perspective, example shape, and failure symptom.

- [x] **Step 2: Explain secret generation and coupling**

Use `openssl rand -hex 32` twice for independent peppers. Explain that MySQL application credentials currently appear in both the MySQL service and WVP environment, and that the ZLM API secret must match Compose/WVP and `docker/media/config.ini`. Never place generated values in the guide.

- [x] **Step 3: Write three complete templates**

Use documentation-reserved addresses: LAN `192.168.10.20`, public `203.0.113.20`, NAT public `198.51.100.20` with LAN `192.168.10.20`. Keep secret values as explicit non-runnable markers such as `REPLACE_WITH_RANDOM_64_HEX_CHARS`.

- [x] **Step 4: Add preflight validation**

Show how to back up `docker/.env`, compare it to a selected example manually, and run `docker compose config --quiet` from `docker/`. Expected success is no output and exit status zero; missing peppers must be explained as a deliberate failure.

### Task 5: Write first deployment and GB28181 onboarding

**Files:**
- Create: `deployment-guide/05-first-deployment.md`
- Create: `deployment-guide/06-gb28181-device-access.md`

**Interfaces:**
- Consumes: Configured `.env`, open ports, and chapter navigation.
- Produces: A running-system procedure for the reader and a staged device-onboarding workflow; this task itself does not run the project.

- [x] **Step 1: Write the fresh-start procedure**

Cover obtaining the repository, entering `docker/`, confirming an empty `volumes/mysql/data`, rendering Compose, pre-pulling or privately mirroring required images when direct Docker Hub access fails, building with `docker compose up -d --build`, checking `docker compose ps`, and following service-specific logs. Explain expected first-build duration without promising a fixed time. Separate image-pull failures from Maven dependency failures so users do not keep changing the wrong mirror.

- [x] **Step 2: Explain database initialization**

Describe `01-wvp.sql`, `02-wvp-authorization.sql`, and `03-wvp-authorization-final.sql` in order. Explain why initialization runs only for an empty MySQL data directory and why deleting the directory is not a repair step.

- [x] **Step 3: Document first login and RBAC bootstrap**

Use the current initial account `admin/admin`, explain raw-password login and automatic BCrypt migration, require immediate password change, then guide creation of departments, roles, functional permissions, video resource groups, and ordinary users while authorization remains in `audit` mode.

- [x] **Step 4: Write the device-field mapping**

Map WVP values to common camera fields: SIP server/platform address, server port, transport, realm/domain, platform ID, device ID, authentication username/password, registration expiry, heartbeat, and local media transport.

- [x] **Step 5: Separate registration, catalog, and playback checks**

For each stage, give symptoms, logs, packet direction, firewall checks, and expected UI state. Note that Dahua and Mercury menu names vary by model/firmware, so the guide describes fields rather than invented click paths.

### Task 6: Write domain, HTTPS, NAT, operations, and backup

**Files:**
- Create: `deployment-guide/07-domain-https-and-nat.md`
- Create: `deployment-guide/08-operations-and-backup.md`

**Interfaces:**
- Consumes: Current Nginx behavior, network scenarios, and persistent mount list.
- Produces: Safe advanced exposure guidance and repeatable day-two operations.

- [x] **Step 1: Explain domain and TLS boundaries**

State that the current Compose publishes only the HTTP management/upstream entry, while protected media tickets prefer the ZLMediaKit-reported `http.sslport=443`. Recommend a maintained external TLS reverse proxy in front of port 8080, preserve `/api/`, `/channel/log`, media paths, WebSocket upgrade headers, and do not expose `/index/hook/`. Explain that uncommenting `MediaHttps` does not enable the current ZLMediaKit URL path. Document a clearly isolated LAN-only HTTP 80/`sslport=0` lab mode, certificate renewal verification, and why HTTPS does not solve SIP/RTP NAT.

- [x] **Step 2: Write NAT decision guidance**

Cover single NAT, double NAT, CGNAT, hairpin NAT, public-to-private port mapping, and address selection for `Stream_IP`, `SDP_IP`, and `SIP_ShowIP`. Include external connectivity checks that do not claim UDP success from a TCP-only tool.

- [x] **Step 3: Write daily Compose operations**

Document status, logs, start, stop, restart, recreate, resource usage, disk checks, and safe configuration reload/recreate behavior. Distinguish `docker compose down` from commands that delete volumes or bind-mounted data.

- [x] **Step 4: Write backup procedures**

Cover MySQL logical dumps, verification that the dump is non-empty and contains schema, backup of `docker/.env`, WVP config/JWK, media configuration, and `volumes/video`. Explain restore as a separate maintenance operation that must first preserve the current state; do not include old-version migration.

### Task 7: Write symptom-based troubleshooting and acceptance

**Files:**
- Create: `deployment-guide/09-troubleshooting.md`
- Create: `deployment-guide/10-acceptance-checklist.md`

**Interfaces:**
- Consumes: Expected states and commands from all earlier chapters.
- Produces: Ordered diagnosis trees and a final deployer-owned record/checklist.

- [x] **Step 1: Build ordered troubleshooting trees**

For installation, Docker CE repository access, Docker Hub image pulls, stale mirror tags, Maven build dependencies, MySQL initialization, WVP restart loops, HTTP/502, login, GB28181 registration, catalog, playback, black screen, one-way media, port conflicts, permissions, SELinux, and disk-full symptoms, list checks from least invasive to most invasive.

- [x] **Step 2: Add evidence-reading examples**

Explain how to read `docker compose ps`, service logs, `ss -lntup`, `df -h`, `free -h`, `docker stats --no-stream`, firewall output, and MySQL initialization logs. Do not fabricate exact log lines that the code does not guarantee.

- [x] **Step 3: Create the acceptance checklist**

Include preflight, container health, Web/login, security, GB28181 registration/catalog/playback, persistence, backup, and reboot checks. Add a parameter record table that stores addresses and locations but never raw secrets.

### Task 8: Verify the complete learning package

**Files:**
- Verify: `deployment-guide/**/*.md`
- Verify: `deployment-guide/examples/*.example`
- Verify: `docs/superpowers/specs/2026-07-16-wvp-deployment-learning-guide-design.md`

**Interfaces:**
- Consumes: The complete guide.
- Produces: A validated, self-contained learning package with no runtime side effects.

- [x] **Step 1: Check structure and links**

List all expected files, verify every relative Markdown link resolves, ensure `README.md` links all ten chapters, and ensure each chapter links back to the guide home and adjacent chapters.

- [x] **Step 2: Check project facts mechanically**

Compare all documented `.env` variable names, Compose service names, SQL filenames, exposed ports, and bind-mount paths against the current project files. Scan for Chinese paths in shell commands that are intended to work cross-platform.

Also verify that every mainland-China mirror URL is linked to its operator's current help page, no closed Docker Hub endpoint appears as an instruction, every system-level mirror edit includes backup, validation, and rollback guidance, and both Maven and npm are represented in the actual first-build dependency path.

- [x] **Step 3: Check safety and unfinished content**

Scan for unfinished markers, real-looking fixed secrets, `chmod 777`, firewall-disable commands, SELinux-disable commands, destructive volume deletion, and claims that HTTPS fixes GB28181 NAT. Any mention must be a warning, not an instruction.

- [x] **Step 4: Review writing quality**

Remove duplicated generic advice, unexplained jargon, unsupported performance numbers, invented device menu paths, and overconfident claims. Ensure each operational command states its working directory and expected result.

- [x] **Step 5: Run final repository hygiene checks**

Run Markdown/static scans and `git diff --check`. Confirm no `target/`, generated logs, temporary folder outside `deployment-guide/`, running Testcontainers container, or spawned subagent remains. Do not run the WVP project and do not commit or push.
