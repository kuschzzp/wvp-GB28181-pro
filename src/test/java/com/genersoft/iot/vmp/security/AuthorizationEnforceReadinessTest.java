package com.genersoft.iot.vmp.security;

import com.genersoft.iot.vmp.auth.model.AuthorizationMode;
import org.h2.tools.RunScript;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationEnforceReadinessTest {

    private static final Path TEST_CONFIG = Path.of("src/test/resources/application-test.yml");
    private static final Path ENDPOINT_MATRIX =
            Path.of("src/test/resources/security/controller-permission-matrix.csv");
    private static final Path H2_SCHEMA = Path.of("数据库/2.8.0-h2/h2-schema.sql");
    private static final Path H2_DATA = Path.of("数据库/2.8.0-h2/h2-data.sql");
    private static final Path NGINX_TEMPLATE = Path.of("docker/nginx/templates/nginx.conf.template");
    private static final Instant CHECK_TIME = Instant.parse("2026-07-16T00:00:00Z");
    private static final Pattern ABL_MAPPING_KEY = Pattern.compile(
            "^authorization\\.media\\.abl-schema-by-network-type(?:\\[|\\.)([0-9]+)\\]?$");

    @Test
    void repositoryConfigurationDatabaseAndMatricesAreReadyForEnforce() throws Exception {
        ConfigState config = readConfig(TEST_CONFIG);
        MatrixState matrix = readEndpointMatrix(ENDPOINT_MATRIX);
        DownloadState download = inspectDownloadExposure(NGINX_TEMPLATE);

        try (Connection connection = openReadyDatabase()) {
            DatabaseState database = inspectDatabase(connection, CHECK_TIME);
            ReadinessSnapshot snapshot = new ReadinessSnapshot(
                    config.mode(),
                    config.allowUrlTokenCompatibility(),
                    config.legacyMediaResponseEnabled(),
                    config.legacyApiKeyEnabled(),
                    config.legacyApiKeySunset(),
                    config.mappedAblNetworkTypes(),
                    database.observedUnmappedAblNetworkTypes(),
                    database.missingAblNetworkTypeEvents(),
                    database.unconfiguredHookNodes(),
                    database.legacyApiKeys(),
                    database.expiredLegacyApiKeys(),
                    database.unboundCloudRecords(),
                    matrix.unclassifiedEndpoints(),
                    matrix.legacyAuditResourceEndpoints(),
                    download.publicExposureCount());

            List<ReadinessViolation> violations = validate(snapshot, CHECK_TIME);
            assertTrue(violations.isEmpty(), () -> "enforce readiness 未通过：\n"
                    + format(violations));
        }
    }

    @Test
    void everyRequiredBlockingConditionProducesAnExplicitFailure() {
        assertViolation("MODE_DISABLED", ready().mode(AuthorizationMode.DISABLED).build());
        assertViolation("UNCLASSIFIED_ENDPOINT", ready().unclassifiedEndpoints(1).build());
        assertViolation("LEGACY_AUDIT_RESOURCE_ENDPOINT",
                ready().legacyAuditResourceEndpoints(1).build());
        assertViolation("URL_TOKEN_COMPATIBILITY_ENABLED",
                ready().allowUrlTokenCompatibility(true).build());
        assertViolation("LEGACY_MEDIA_RESPONSE_ENABLED",
                ready().legacyMediaResponseEnabled(true).build());
        assertViolation("ABL_NETWORK_TYPE_UNMAPPED",
                ready().observedUnmappedAblNetworkTypes(Set.of(7)).build());
        assertViolation("ABL_NETWORK_TYPE_MISSING",
                ready().missingAblNetworkTypeEvents(1).build());
        assertViolation("MEDIA_NODE_HOOK_SECRET_MISSING",
                ready().unconfiguredHookNodes(1).build());
        assertViolation("LEGACY_API_KEY_EXPIRED",
                ready().legacyApiKeyEnabled(true).expiredLegacyApiKeys(1).build());
        assertViolation("LEGACY_API_KEY_WINDOW_CLOSED",
                ready().legacyApiKeyEnabled(true)
                        .legacyApiKeySunset(Instant.parse("2026-07-15T00:00:00Z"))
                        .legacyApiKeys(1)
                        .build());
        assertViolation("CLOUD_RECORD_UNBOUND", ready().unboundCloudRecords(1).build());
        assertViolation("PUBLIC_DOWNLOAD_EXPOSURE", ready().publicDownloadExposureCount(1).build());
    }

    @Test
    void anObservedAblNetworkTypeIsReadyOnlyAfterItHasAnExplicitSchemaMapping() {
        ReadinessSnapshot mapped = ready()
                .mappedAblNetworkTypes(Set.of(7))
                .observedUnmappedAblNetworkTypes(Set.of(7))
                .build();

        assertTrue(validate(mapped, CHECK_TIME).isEmpty());
    }

    private static Connection openReadyDatabase() throws Exception {
        String databaseName = "wvp_readiness_" + UUID.randomUUID().toString().replace("-", "");
        Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:" + databaseName
                        + ";MODE=MySQL;DB_CLOSE_DELAY=0;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        try {
            RunScript.execute(connection, Files.newBufferedReader(H2_SCHEMA, StandardCharsets.UTF_8));
            RunScript.execute(connection, Files.newBufferedReader(H2_DATA, StandardCharsets.UTF_8));
            execute(connection, """
                    update wvp_cloud_record
                       set source_type = 'CHANNEL',
                           source_id = 42,
                           channel_id = 42,
                           source_reason = 'READINESS_TEST_BOUND'
                    """);
            try (PreparedStatement statement = connection.prepareStatement("""
                    insert into wvp_media_server
                        (id, ip, type, status, hook_secret_hash, hook_secret_version,
                         hook_auth_enabled, update_time)
                    values (?, ?, ?, ?, ?, ?, ?, ?)
                    """)) {
                statement.setString(1, "readiness-node");
                statement.setString(2, "127.0.0.1");
                statement.setString(3, "zlm");
                statement.setBoolean(4, true);
                statement.setString(5,
                        "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
                statement.setLong(6, 1L);
                statement.setBoolean(7, true);
                statement.setString(8, "2026-07-16 00:00:00");
                statement.executeUpdate();
            }
            return connection;
        } catch (Exception exception) {
            connection.close();
            throw exception;
        }
    }

    private static ConfigState readConfig(Path path) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        var sources = loader.load("authorization-readiness", new FileSystemResource(path));
        for (int index = sources.size() - 1; index >= 0; index--) {
            environment.getPropertySources().addFirst(sources.get(index));
        }
        String configuredMode = environment.getProperty("authorization.mode");
        if (configuredMode == null || configuredMode.isBlank()) {
            throw new IllegalStateException(path + " 缺少 authorization.mode 配置");
        }
        Set<Integer> mappedNetworkTypes = new HashSet<>();
        for (var source : sources) {
            if (!(source instanceof EnumerablePropertySource<?> enumerable)) {
                continue;
            }
            for (String propertyName : enumerable.getPropertyNames()) {
                Matcher matcher = ABL_MAPPING_KEY.matcher(propertyName);
                if (matcher.matches()) {
                    mappedNetworkTypes.add(Integer.parseInt(matcher.group(1)));
                }
            }
        }
        return new ConfigState(
                AuthorizationMode.valueOf(configuredMode.trim().toUpperCase(Locale.ROOT)),
                environment.getProperty(
                        "authorization.allow-url-token-compatibility", Boolean.class, false),
                environment.getProperty(
                        "authorization.media.legacy-response-enabled", Boolean.class, false),
                environment.getProperty(
                        "authorization.legacy-api-key-enabled", Boolean.class, false),
                Instant.parse(environment.getProperty(
                        "authorization.legacy-api-key-sunset", "2026-10-01T00:00:00Z")),
                Set.copyOf(mappedNetworkTypes));
    }

    private static MatrixState readEndpointMatrix(Path path) throws IOException {
        Set<String> allowedClassifications = Set.of(
                "PUBLIC", "INTERNAL", "MEDIA_HOOK", "AUTHENTICATED",
                "REQUIRE_PERMISSION", "REQUIRE_RESOURCE");
        long unclassified = 0;
        long legacyAuditResources = 0;
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            assertEquals(
                    "handlerClass,handlerMethod,httpMethod,path,classification,permissionCode,resourceResolver,compatibilityStatus",
                    reader.readLine(),
                    "端点矩阵表头发生未审查的变化");
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", -1);
                if (columns.length != 8 || !allowedClassifications.contains(columns[4])) {
                    unclassified++;
                    continue;
                }
                if ("REQUIRE_RESOURCE".equals(columns[4])
                        && "LEGACY_AUDIT".equals(columns[7])) {
                    legacyAuditResources++;
                }
            }
        }
        return new MatrixState(unclassified, legacyAuditResources);
    }

    private static DownloadState inspectDownloadExposure(Path path) throws IOException {
        String nginx = Files.readString(path, StandardCharsets.UTF_8);
        long exposures = 0;
        for (String forbiddenLocation : List.of(
                "location /record_proxy",
                "location ^~ /record_proxy",
                "location /mp4_record",
                "location ^~ /mp4_record",
                "location /mediaserver/api/downloadFile",
                "location ^~ /mediaserver/api/downloadFile")) {
            if (nginx.contains(forbiddenLocation)) {
                exposures++;
            }
        }
        String protectedMarker = "location ^~ /_protected_media/";
        int protectedStart = nginx.indexOf(protectedMarker);
        int protectedEnd = protectedStart < 0 ? -1 : nginx.indexOf('}', protectedStart);
        if (protectedStart < 0 || protectedEnd < 0
                || !nginx.substring(protectedStart, protectedEnd).contains("internal;")) {
            exposures++;
        }
        return new DownloadState(exposures);
    }

    private static DatabaseState inspectDatabase(Connection connection, Instant now)
            throws SQLException {
        Set<Integer> observedUnmappedNetworkTypes = new HashSet<>();
        long missingNetworkTypeEvents = 0;
        try (PreparedStatement statement = connection.prepareStatement("""
                select reason_code
                  from wvp_authz_audit
                 where reason_code = 'ABL_NETWORK_TYPE_MISSING'
                    or reason_code like 'ABL_NETWORK_TYPE_UNMAPPED_%'
                """);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String reason = resultSet.getString(1);
                if ("ABL_NETWORK_TYPE_MISSING".equals(reason)) {
                    missingNetworkTypeEvents++;
                } else {
                    String suffix = reason.substring("ABL_NETWORK_TYPE_UNMAPPED_".length());
                    try {
                        observedUnmappedNetworkTypes.add(Integer.parseInt(suffix));
                    } catch (NumberFormatException exception) {
                        missingNetworkTypeEvents++;
                    }
                }
            }
        }
        long unconfiguredHookNodes = scalarLong(connection, """
                select count(*)
                  from wvp_media_server
                 where hook_auth_enabled is not true
                    or hook_secret_hash is null
                    or trim(hook_secret_hash) = ''
                    or hook_secret_version <= 0
                """);
        long legacyApiKeys = scalarLong(connection,
                "select count(*) from wvp_user_api_key where status = 'LEGACY'");
        long expiredLegacyApiKeys;
        try (PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                  from wvp_user_api_key
                 where status = 'LEGACY'
                   and expired_at is not null
                   and expired_at <= ?
                """)) {
            statement.setLong(1, now.toEpochMilli());
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                expiredLegacyApiKeys = resultSet.getLong(1);
            }
        }
        long unboundCloudRecords = scalarLong(connection, """
                select count(*)
                  from wvp_cloud_record
                 where source_type is null
                    or source_type = 'LEGACY_UNBOUND'
                    or source_id is null
                    or channel_id is null
                """);
        return new DatabaseState(
                Set.copyOf(observedUnmappedNetworkTypes),
                missingNetworkTypeEvents,
                unconfiguredHookNodes,
                legacyApiKeys,
                expiredLegacyApiKeys,
                unboundCloudRecords);
    }

    private static List<ReadinessViolation> validate(ReadinessSnapshot snapshot, Instant now) {
        List<ReadinessViolation> violations = new ArrayList<>();
        addIf(violations, snapshot.mode() == AuthorizationMode.DISABLED,
                "MODE_DISABLED", "authorization.mode 不能为 disabled");
        addIf(violations, snapshot.unclassifiedEndpoints() > 0,
                "UNCLASSIFIED_ENDPOINT", "存在未分类 HTTP 端点");
        addIf(violations, snapshot.legacyAuditResourceEndpoints() > 0,
                "LEGACY_AUDIT_RESOURCE_ENDPOINT", "资源端点仍处于 LEGACY_AUDIT");
        addIf(violations, snapshot.allowUrlTokenCompatibility(),
                "URL_TOKEN_COMPATIBILITY_ENABLED", "URL access-token 兼容仍启用");
        addIf(violations, snapshot.legacyMediaResponseEnabled(),
                "LEGACY_MEDIA_RESPONSE_ENABLED", "媒体响应仍生成长期 URL");

        Set<Integer> unmapped = new HashSet<>(snapshot.observedUnmappedAblNetworkTypes());
        unmapped.removeAll(snapshot.mappedAblNetworkTypes());
        addIf(violations, !unmapped.isEmpty(),
                "ABL_NETWORK_TYPE_UNMAPPED", "未映射 ABL networkType: " + unmapped);
        addIf(violations, snapshot.missingAblNetworkTypeEvents() > 0,
                "ABL_NETWORK_TYPE_MISSING", "ABL Hook 缺少 networkType");
        addIf(violations, snapshot.unconfiguredHookNodes() > 0,
                "MEDIA_NODE_HOOK_SECRET_MISSING", "存在未启用 Hook 认证或未配置密钥的媒体节点");

        addIf(violations, snapshot.expiredLegacyApiKeys() > 0,
                "LEGACY_API_KEY_EXPIRED", "数据库仍有已过期 LEGACY API Key");
        addIf(violations,
                snapshot.legacyApiKeys() > 0
                        && (!snapshot.legacyApiKeyEnabled()
                        || !now.isBefore(snapshot.legacyApiKeySunset())),
                "LEGACY_API_KEY_WINDOW_CLOSED", "LEGACY API Key 不在批准的兼容窗口内");
        addIf(violations, snapshot.unboundCloudRecords() > 0,
                "CLOUD_RECORD_UNBOUND", "存在未绑定视频来源的云录像");
        addIf(violations, snapshot.publicDownloadExposureCount() > 0,
                "PUBLIC_DOWNLOAD_EXPOSURE", "Nginx 存在公开媒体文件路径或缺少 internal 保护");
        return List.copyOf(violations);
    }

    private static void addIf(
            List<ReadinessViolation> violations,
            boolean condition,
            String code,
            String message) {
        if (condition) {
            violations.add(new ReadinessViolation(code, message));
        }
    }

    private static void assertViolation(String expectedCode, ReadinessSnapshot snapshot) {
        List<ReadinessViolation> violations = validate(snapshot, CHECK_TIME);
        assertTrue(violations.stream().anyMatch(violation -> expectedCode.equals(violation.code())),
                () -> "缺少 readiness 失败码 " + expectedCode + "，实际：" + format(violations));
    }

    private static ReadinessBuilder ready() {
        return new ReadinessBuilder();
    }

    private static String format(List<ReadinessViolation> violations) {
        return violations.stream()
                .map(violation -> violation.code() + ": " + violation.message())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("无");
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record ConfigState(
            AuthorizationMode mode,
            boolean allowUrlTokenCompatibility,
            boolean legacyMediaResponseEnabled,
            boolean legacyApiKeyEnabled,
            Instant legacyApiKeySunset,
            Set<Integer> mappedAblNetworkTypes) {
    }

    private record MatrixState(long unclassifiedEndpoints, long legacyAuditResourceEndpoints) {
    }

    private record DownloadState(long publicExposureCount) {
    }

    private record DatabaseState(
            Set<Integer> observedUnmappedAblNetworkTypes,
            long missingAblNetworkTypeEvents,
            long unconfiguredHookNodes,
            long legacyApiKeys,
            long expiredLegacyApiKeys,
            long unboundCloudRecords) {
    }

    private record ReadinessViolation(String code, String message) {
    }

    private record ReadinessSnapshot(
            AuthorizationMode mode,
            boolean allowUrlTokenCompatibility,
            boolean legacyMediaResponseEnabled,
            boolean legacyApiKeyEnabled,
            Instant legacyApiKeySunset,
            Set<Integer> mappedAblNetworkTypes,
            Set<Integer> observedUnmappedAblNetworkTypes,
            long missingAblNetworkTypeEvents,
            long unconfiguredHookNodes,
            long legacyApiKeys,
            long expiredLegacyApiKeys,
            long unboundCloudRecords,
            long unclassifiedEndpoints,
            long legacyAuditResourceEndpoints,
            long publicDownloadExposureCount) {
    }

    private static final class ReadinessBuilder {
        private AuthorizationMode mode = AuthorizationMode.AUDIT;
        private boolean allowUrlTokenCompatibility;
        private boolean legacyMediaResponseEnabled;
        private boolean legacyApiKeyEnabled;
        private Instant legacyApiKeySunset = Instant.parse("2026-10-01T00:00:00Z");
        private Set<Integer> mappedAblNetworkTypes = Set.of();
        private Set<Integer> observedUnmappedAblNetworkTypes = Set.of();
        private long missingAblNetworkTypeEvents;
        private long unconfiguredHookNodes;
        private long legacyApiKeys;
        private long expiredLegacyApiKeys;
        private long unboundCloudRecords;
        private long unclassifiedEndpoints;
        private long legacyAuditResourceEndpoints;
        private long publicDownloadExposureCount;

        private ReadinessBuilder mode(AuthorizationMode value) {
            mode = value;
            return this;
        }

        private ReadinessBuilder allowUrlTokenCompatibility(boolean value) {
            allowUrlTokenCompatibility = value;
            return this;
        }

        private ReadinessBuilder legacyMediaResponseEnabled(boolean value) {
            legacyMediaResponseEnabled = value;
            return this;
        }

        private ReadinessBuilder legacyApiKeyEnabled(boolean value) {
            legacyApiKeyEnabled = value;
            return this;
        }

        private ReadinessBuilder legacyApiKeySunset(Instant value) {
            legacyApiKeySunset = value;
            return this;
        }

        private ReadinessBuilder mappedAblNetworkTypes(Set<Integer> value) {
            mappedAblNetworkTypes = Set.copyOf(value);
            return this;
        }

        private ReadinessBuilder observedUnmappedAblNetworkTypes(Set<Integer> value) {
            observedUnmappedAblNetworkTypes = Set.copyOf(value);
            return this;
        }

        private ReadinessBuilder missingAblNetworkTypeEvents(long value) {
            missingAblNetworkTypeEvents = value;
            return this;
        }

        private ReadinessBuilder unconfiguredHookNodes(long value) {
            unconfiguredHookNodes = value;
            return this;
        }

        private ReadinessBuilder legacyApiKeys(long value) {
            legacyApiKeys = value;
            return this;
        }

        private ReadinessBuilder expiredLegacyApiKeys(long value) {
            expiredLegacyApiKeys = value;
            return this;
        }

        private ReadinessBuilder unboundCloudRecords(long value) {
            unboundCloudRecords = value;
            return this;
        }

        private ReadinessBuilder unclassifiedEndpoints(long value) {
            unclassifiedEndpoints = value;
            return this;
        }

        private ReadinessBuilder legacyAuditResourceEndpoints(long value) {
            legacyAuditResourceEndpoints = value;
            return this;
        }

        private ReadinessBuilder publicDownloadExposureCount(long value) {
            publicDownloadExposureCount = value;
            return this;
        }

        private ReadinessSnapshot build() {
            return new ReadinessSnapshot(
                    mode,
                    allowUrlTokenCompatibility,
                    legacyMediaResponseEnabled,
                    legacyApiKeyEnabled,
                    legacyApiKeySunset,
                    mappedAblNetworkTypes,
                    observedUnmappedAblNetworkTypes,
                    missingAblNetworkTypeEvents,
                    unconfiguredHookNodes,
                    legacyApiKeys,
                    expiredLegacyApiKeys,
                    unboundCloudRecords,
                    unclassifiedEndpoints,
                    legacyAuditResourceEndpoints,
                    publicDownloadExposureCount);
        }
    }
}
