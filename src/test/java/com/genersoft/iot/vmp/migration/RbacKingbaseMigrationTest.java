package com.genersoft.iot.vmp.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("kingbase-contract")
class RbacKingbaseMigrationTest {

    @Test
    void initializationAndExpandMigrationSatisfyTheSameContractOnDisposableDatabase() throws Exception {
        String url = System.getenv("WVP_KINGBASE_JDBC_URL");
        String username = System.getenv("WVP_KINGBASE_USERNAME");
        String password = System.getenv("WVP_KINGBASE_PASSWORD");
        assumeTrue(notBlank(url) && notBlank(username) && notBlank(password),
                "未配置专用 Kingbase 测试库，跳过 kingbase-contract");
        assertTrue(isClearlyDisposable(url),
                "WVP_KINGBASE_JDBC_URL 必须明确包含 test、contract、ci、scratch 或 disposable 标识");

        String schema = "wvp_contract_" + UUID.randomUUID().toString().replace("-", "");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "create schema " + schema);
            try {
                execute(connection, "set search_path to " + schema);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.POSTGRESQL_BASE);
                MigrationContractSupport.seedLegacyCloudRecord(connection);
                MigrationContractSupport.dropLegacyAuthorizationTablesForFreshInit(connection, false);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.KINGBASE_INIT);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.KINGBASE_INIT);
                MigrationContractSupport.verifyAuthorizationContract(connection, false);
            } finally {
                execute(connection, "set search_path to public");
                execute(connection, "drop schema if exists " + schema + " cascade");
            }
        }

        String upgradeSchema = schema + "_upgrade";
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "create schema " + upgradeSchema);
            try {
                execute(connection, "set search_path to " + upgradeSchema);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.POSTGRESQL_BASE);
                MigrationContractSupport.seedLegacyAuthorizationData(connection);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.KINGBASE_UPDATE);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.KINGBASE_UPDATE);
                MigrationContractSupport.verifyAuthorizationContract(connection, true);
                MigrationContractSupport.prepareForContract(connection);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.KINGBASE_CONTRACT);
                MigrationContractSupport.runJdbcScript(connection, MigrationContractSupport.KINGBASE_CONTRACT);
                MigrationContractSupport.verifyContractedSchema(connection);
            } finally {
                execute(connection, "set search_path to public");
                execute(connection, "drop schema if exists " + upgradeSchema + " cascade");
            }
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean isClearlyDisposable(String url) {
        String normalized = url.toLowerCase(Locale.ROOT);
        return normalized.contains("test")
                || normalized.contains("contract")
                || normalized.contains("ci")
                || normalized.contains("scratch")
                || normalized.contains("disposable");
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
