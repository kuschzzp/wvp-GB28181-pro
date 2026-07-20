# WVP 2.8 Fresh Docker Installation Implementation Plan

> **Execution mode:** Execute this plan inline in the current task. Do not commit or push any changes.

**Goal:** Make `docker/docker-compose.yml` support a self-contained, brand-new WVP 2.8 deployment without Chinese host paths or a separate database-upgrade procedure.

**Architecture:** Keep the existing business bootstrap SQL as step 01, copy the reviewed 2.8 authorization expand and contract SQL into Docker-owned English paths as steps 02 and 03, then let the MySQL image execute all three files in filename order on an empty data directory. Docker runtime configuration starts from the final role-relation schema, so legacy role dual-write is disabled.

**Tech Stack:** Docker Compose, MySQL 8.4, Spring Boot YAML, JUnit 5, Testcontainers.

---

### Task 1: Lock the fresh-install contract in tests

**Files:**
- Modify: `src/test/java/com/genersoft/iot/vmp/migration/MigrationContractSupport.java`
- Modify: `src/test/java/com/genersoft/iot/vmp/migration/RbacMySqlMigrationTest.java`
- Modify: `src/test/java/com/genersoft/iot/vmp/migration/MediaAuthorizationSchemaContractTest.java`

**Step 1: Replace the old chained-bootstrap expectation**

Declare Docker-local paths for all three SQL files and make `runDockerMySqlBootstrap` execute them in order:

```java
static final Path DOCKER_MYSQL_BOOTSTRAP = Path.of("docker/mysql/db/wvp.sql");
static final Path DOCKER_MYSQL_AUTHORIZATION = Path.of("docker/mysql/db/wvp-authorization.sql");
static final Path DOCKER_MYSQL_AUTHORIZATION_FINAL =
        Path.of("docker/mysql/db/wvp-authorization-final.sql");
```

**Step 2: Assert the Compose and runtime contract**

Verify the Compose file mounts only the three English Docker-local SQL paths, contains no `../数据库/` reference, the base SQL contains no `source` command, and both Docker WVP configurations set `legacy-role-dual-write-enabled: false`.

**Step 3: Verify the Docker database ends in the contracted schema**

Update the MySQL Testcontainers scenario so the Docker bootstrap runs all three scripts and calls `verifyContractedSchema`.

**Step 4: Run the focused static contract and confirm it fails before implementation**

Run:

```bash
mvn -q -Pdb-contract -Dtest=MediaAuthorizationSchemaContractTest test
```

Expected: failure because the English SQL files and new Compose mounts do not exist yet.

### Task 2: Implement the self-contained Docker bootstrap

**Files:**
- Create: `docker/mysql/db/wvp-authorization.sql`
- Create: `docker/mysql/db/wvp-authorization-final.sql`
- Modify: `docker/mysql/db/wvp.sql`
- Modify: `docker/docker-compose.yml`
- Modify: `docker/wvp/wvp/application-base.yml`
- Modify: `docker/wvp/wvp/application-docker.yml`
- Modify: `src/main/resources/application-docker.yml`

**Step 1: Add reviewed Docker-local authorization scripts**

Mechanically copy the current reviewed MySQL 2.8 expand and contract scripts into the two English Docker-owned files. Confirm byte-for-byte equality with the canonical scripts.

**Step 2: Make MySQL entrypoint order explicit**

Mount:

```yaml
- ./mysql/db/wvp.sql:/docker-entrypoint-initdb.d/01-wvp.sql:ro
- ./mysql/db/wvp-authorization.sql:/docker-entrypoint-initdb.d/02-wvp-authorization.sql:ro
- ./mysql/db/wvp-authorization-final.sql:/docker-entrypoint-initdb.d/03-wvp-authorization-final.sql:ro
```

Remove the `source` command from `wvp.sql`; MySQL's entrypoint now owns sequencing.

**Step 3: Select the final authorization runtime mode**

Set `legacy-role-dual-write-enabled: false` in both Docker WVP YAML files because step 03 has already converted `wvp_user_role` to the final relation table.

### Task 3: Document and verify the fresh deployment

**Files:**
- Modify: `docker/README.md`
- Modify if stale: `README.md`
- Update: `docs/superpowers/specs/2026-07-16-wvp-docker-fresh-install-design.md`

**Step 1: Rewrite Docker initialization instructions**

Document the empty-data-directory prerequisite, the three English SQL files and their order, the two mandatory peppers, and the fact that existing-volume upgrades are intentionally outside this deployment flow.

**Step 2: Validate Compose without starting the project**

Run:

```bash
MEDIA_HOOK_SECRET_PEPPER=0123456789abcdef0123456789abcdef \
API_KEY_PEPPER=abcdef0123456789abcdef0123456789 \
docker compose -f docker/docker-compose.yml config --quiet
```

**Step 3: Run database contracts with disposable containers**

Run:

```bash
mvn -q -Pdb-contract \
  -Dtest=RbacMySqlMigrationTest,RbacPostgreSqlMigrationTest,MediaAuthorizationSchemaContractTest test
```

Expected: Docker-local MySQL fresh bootstrap, canonical upgrade paths, PostgreSQL contracts, and static Docker contract all pass.

**Step 4: Perform static hygiene checks**

Run checks for Chinese Compose SQL paths, leftover MySQL `source` commands, YAML dual-write settings, missing Docker SQL files, and `git diff --check`.

**Step 5: Clean validation artifacts**

Stop/remove any disposable validation containers, run Maven clean, remove test logs created during validation, and confirm no temporary service or folder remains. Do not start the WVP application and do not commit or push.

## Execution Result

Implemented inline on 2026-07-16. Compose parsing and all four selected database contract tests passed. Cleanup and final static checks are recorded in the task handoff; no Git commit or push is part of this plan.
