package com.genersoft.iot.vmp.auth;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VideoAuthorizationMatrixTest {

    private static final String ENDPOINT_MATRIX =
            "/security/controller-permission-matrix.csv";
    private static final Path VIDEO_MATRIX = Path.of(
            "docs/security/video-resource-authorization-matrix.md");

    @Test
    void readableVideoMatrixCoversEveryResourceEndpointExactlyOnce()
            throws Exception {
        Map<EndpointKey, AuthorizationEntry> expected = readResourceEndpoints();
        Map<EndpointKey, AuthorizationEntry> documented = readVideoMatrix();

        assertFalse(expected.isEmpty(), "资源端点清单不能为空");
        assertEquals(expected, documented,
                "可读视频授权矩阵必须与机器端点矩阵中的 REQUIRE_RESOURCE 行完全一致");
    }

    private Map<EndpointKey, AuthorizationEntry> readResourceEndpoints()
            throws Exception {
        InputStream stream = getClass().getResourceAsStream(ENDPOINT_MATRIX);
        assertNotNull(stream, ENDPOINT_MATRIX + " 不存在");
        Map<EndpointKey, AuthorizationEntry> rows = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            reader.readLine();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", -1);
                if (!"REQUIRE_RESOURCE".equals(columns[4])) {
                    continue;
                }
                EndpointKey key = new EndpointKey(columns[2], columns[3]);
                AuthorizationEntry previous = rows.put(key,
                        new AuthorizationEntry(columns[5], columns[6]));
                assertTrue(previous == null,
                        "资源端点 Method/Path 重复: " + key);
            }
        }
        return Map.copyOf(rows);
    }

    private Map<EndpointKey, AuthorizationEntry> readVideoMatrix()
            throws Exception {
        assertTrue(Files.isRegularFile(VIDEO_MATRIX), VIDEO_MATRIX + " 不存在");
        Map<EndpointKey, AuthorizationEntry> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(VIDEO_MATRIX, StandardCharsets.UTF_8)) {
            if (!line.matches("^\\| `(?:GET|POST|PUT|DELETE|PATCH|ANY)` \\|.*")) {
                continue;
            }
            String[] columns = line.split("\\|", -1);
            assertEquals(9, columns.length, "视频授权矩阵列数错误: " + line);
            String method = code(columns[1]);
            String path = code(columns[2]);
            String permission = code(columns[3]);
            String resolver = code(columns[4]);
            assertFalse(columns[5].trim().isEmpty(), "DenialStatus 不能为空");
            assertFalse(columns[6].trim().isEmpty(), "ListScope 不能为空");
            assertFalse(columns[7].trim().isEmpty(), "BatchPolicy 不能为空");
            EndpointKey key = new EndpointKey(method, path);
            AuthorizationEntry previous = rows.put(
                    key, new AuthorizationEntry(permission, resolver));
            assertTrue(previous == null, "视频授权矩阵重复端点: " + key);
        }
        return Map.copyOf(rows);
    }

    private String code(String column) {
        String value = column.trim();
        assertTrue(value.startsWith("`") && value.endsWith("`"),
                "机器字段必须使用反引号: " + column);
        return value.substring(1, value.length() - 1);
    }

    private record EndpointKey(String method, String path) {
    }

    private record AuthorizationEntry(String permission, String resolver) {
    }
}
