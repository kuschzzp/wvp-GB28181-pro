package com.genersoft.iot.vmp.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;

@Tag("db-contract")
@Testcontainers
class RbacMySqlMigrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4.8")
            .withDatabaseName("wvp")
            .withUsername("wvp_contract")
            .withPassword("wvp_contract_password");

    @Test
    void initializationAndExpandMigrationSatisfyTheSameContract() throws Exception {
        try (Connection connection = MYSQL.createConnection("")) {
            MigrationContractSupport.resetMySql(connection);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_BASE);
            MigrationContractSupport.seedLegacyCloudRecord(connection);
            MigrationContractSupport.dropLegacyAuthorizationTablesForFreshInit(connection, true);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_INIT);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_INIT);
            MigrationContractSupport.verifyAuthorizationContract(connection, false);

            MigrationContractSupport.resetMySql(connection);
            MigrationContractSupport.runDockerMySqlBootstrap(MYSQL);
            MigrationContractSupport.verifyContractedSchema(connection);

            MigrationContractSupport.resetMySql(connection);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_BASE);
            MigrationContractSupport.seedLegacyAuthorizationData(connection);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_UPDATE);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_UPDATE);
            MigrationContractSupport.verifyAuthorizationContract(connection, true);
            MigrationContractSupport.assertMySqlScriptFails(
                    MYSQL,
                    MigrationContractSupport.MYSQL_CONTRACT,
                    "LEGACY API Key remains");
            MigrationContractSupport.prepareForContract(connection);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_CONTRACT);
            MigrationContractSupport.runMySqlScript(MYSQL, MigrationContractSupport.MYSQL_CONTRACT);
            MigrationContractSupport.verifyContractedSchema(connection);
        }
    }
}
