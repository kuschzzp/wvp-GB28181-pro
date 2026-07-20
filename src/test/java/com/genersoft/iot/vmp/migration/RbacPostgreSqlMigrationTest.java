package com.genersoft.iot.vmp.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;

@Tag("db-contract")
@Testcontainers
class RbacPostgreSqlMigrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRESQL = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("wvp_contract")
            .withUsername("wvp_contract")
            .withPassword("wvp_contract_password");

    @Test
    void initializationAndExpandMigrationSatisfyTheSameContract() throws Exception {
        try (Connection connection = POSTGRESQL.createConnection("")) {
            MigrationContractSupport.resetPostgreSql(connection);
            MigrationContractSupport.runPostgreSqlScript(POSTGRESQL, MigrationContractSupport.POSTGRESQL_BASE);
            MigrationContractSupport.seedLegacyCloudRecord(connection);
            MigrationContractSupport.dropLegacyAuthorizationTablesForFreshInit(connection, false);
            MigrationContractSupport.runPostgreSqlScript(POSTGRESQL, MigrationContractSupport.POSTGRESQL_INIT);
            MigrationContractSupport.runPostgreSqlScript(POSTGRESQL, MigrationContractSupport.POSTGRESQL_INIT);
            MigrationContractSupport.verifyAuthorizationContract(connection, false);

            MigrationContractSupport.resetPostgreSql(connection);
            MigrationContractSupport.runPostgreSqlScript(POSTGRESQL, MigrationContractSupport.POSTGRESQL_BASE);
            MigrationContractSupport.seedLegacyAuthorizationData(connection);
            MigrationContractSupport.runPostgreSqlScript(POSTGRESQL, MigrationContractSupport.POSTGRESQL_UPDATE);
            MigrationContractSupport.runPostgreSqlScript(POSTGRESQL, MigrationContractSupport.POSTGRESQL_UPDATE);
            MigrationContractSupport.verifyAuthorizationContract(connection, true);
            MigrationContractSupport.assertPostgreSqlScriptFails(
                    POSTGRESQL,
                    MigrationContractSupport.POSTGRESQL_CONTRACT,
                    "LEGACY API Key remains");
            MigrationContractSupport.prepareForContract(connection);
            MigrationContractSupport.runPostgreSqlScript(
                    POSTGRESQL, MigrationContractSupport.POSTGRESQL_CONTRACT);
            MigrationContractSupport.runPostgreSqlScript(
                    POSTGRESQL, MigrationContractSupport.POSTGRESQL_CONTRACT);
            MigrationContractSupport.verifyContractedSchema(connection);
        }
    }
}
