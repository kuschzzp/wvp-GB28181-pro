package com.genersoft.iot.vmp.migration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("db-contract")
@JdbcTest
@ActiveProfiles("test")
class MediaAuthorizationSchemaContractTest {

    @Autowired
    private DataSource dataSource;

    @Test
    void h2SchemaSatisfiesTheSameLogicalAuthorizationContract() throws Exception {
        new ResourceDatabasePopulator(
                new FileSystemResource("数据库/2.8.0-h2/h2-schema.sql"),
                new FileSystemResource("数据库/2.8.0-h2/h2-data.sql"))
                .execute(dataSource);

        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            MigrationContractSupport.seedLegacyCloudRecord(connection);
            MigrationContractSupport.verifyAuthorizationContract(connection, false);
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    @Test
    void dockerFreshBootstrapUsesOnlyDockerOwnedEnglishPaths() throws Exception {
        String compose = Files.readString(Path.of("docker/docker-compose.yml"));
        String bootstrap = Files.readString(MigrationContractSupport.DOCKER_MYSQL_BOOTSTRAP);
        String authorization = Files.readString(MigrationContractSupport.DOCKER_MYSQL_AUTHORIZATION);
        String authorizationFinal = Files.readString(
                MigrationContractSupport.DOCKER_MYSQL_AUTHORIZATION_FINAL);
        String baseConfig = Files.readString(Path.of("docker/wvp/wvp/application-base.yml"));
        String dockerConfig = Files.readString(Path.of("docker/wvp/wvp/application-docker.yml"));
        String classpathDockerConfig = Files.readString(
                Path.of("src/main/resources/application-docker.yml"));

        assertTrue(compose.contains("./mysql/db/wvp.sql:/docker-entrypoint-initdb.d/01-wvp.sql:ro"));
        assertTrue(compose.contains("./mysql/db/wvp-authorization.sql:"
                + "/docker-entrypoint-initdb.d/02-wvp-authorization.sql:ro"));
        assertTrue(compose.contains("./mysql/db/wvp-authorization-final.sql:"
                + "/docker-entrypoint-initdb.d/03-wvp-authorization-final.sql:ro"));
        assertTrue(!compose.contains("../数据库/"), "Docker Compose 不能依赖中文宿主机路径");
        assertTrue(!bootstrap.matches("(?s).*\\n\\s*source\\s+.*"),
                "基础脚本不能再通过 source 串联外部迁移");
        assertTrue(authorization.contains("create table if not exists wvp_role"));
        assertTrue(authorization.contains("create table if not exists wvp_user_role_rel"));
        assertTrue(authorizationFinal.contains("create procedure wvp_contract_rbac_2_8_0()"));
        assertTrue(authorizationFinal.contains("rename table wvp_user_role "));
        assertTrue(baseConfig.contains("legacy-role-dual-write-enabled: false"));
        assertTrue(dockerConfig.contains("legacy-role-dual-write-enabled: false"));
        assertTrue(classpathDockerConfig.contains("legacy-role-dual-write-enabled: false"));
        assertTrue(!baseConfig.contains("legacy-role-dual-write-enabled: true"));
        assertTrue(!dockerConfig.contains("legacy-role-dual-write-enabled: true"));
        assertTrue(!classpathDockerConfig.contains("legacy-role-dual-write-enabled: true"));
    }
}
