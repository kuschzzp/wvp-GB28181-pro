package com.genersoft.iot.vmp.auth;

import com.genersoft.iot.vmp.auth.annotation.InternalEndpoint;
import com.genersoft.iot.vmp.auth.annotation.MediaHookEndpoint;
import com.genersoft.iot.vmp.auth.annotation.PublicEndpoint;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.context.support.StaticWebApplicationContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndpointClassificationTest {

    private static final String BASE_PACKAGE = "com.genersoft.iot.vmp";
    private static final String MATRIX_RESOURCE = "/security/controller-permission-matrix.csv";
    private static final Pattern PERMISSION_CODE = Pattern.compile(
            "^[a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9-]*$");
    private static final Pattern AUTHZ_EXPRESSION = Pattern.compile(
            "^@authz\\.(check|require)\\('([a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9-]*)'\\)$");
    private static final Pattern SEEDED_PERMISSION = Pattern.compile(
            "'([a-z][a-z0-9-]*:[a-z][a-z0-9-]*:[A-Za-z][A-Za-z0-9-]*)'");
    private static final Set<String> MARKER_CLASSIFICATIONS = Set.of(
            "PUBLIC", "INTERNAL", "MEDIA_HOOK");
    private static final Set<String> PROTECTED_CLASSIFICATIONS = Set.of(
            "AUTHENTICATED", "REQUIRE_PERMISSION", "REQUIRE_RESOURCE");
    private static final Set<String> RESOURCE_RESOLVERS = Set.of(
            "ChannelResourceResolver",
            "StreamResourceResolver",
            "RecordResourceResolver",
            "AlarmResourceResolver");
    private static final Set<String> COMPATIBILITY_STATUSES = Set.of(
            "ACTIVE", "DEPRECATED", "LEGACY_AUDIT", "DEFERRED_MEDIA_AUTH");

    @Test
    void everyRepositoryEndpointHasExactlyOneCurrentMatrixEntry() throws Exception {
        List<DiscoveredEndpoint> endpoints = discoverEndpoints();
        Map<EndpointKey, MatrixRow> matrix = readMatrix();

        Set<EndpointKey> discoveredKeys = new TreeSet<>(Comparator.comparing(EndpointKey::serialized));
        endpoints.stream().map(DiscoveredEndpoint::key).forEach(discoveredKeys::add);
        assertEquals(discoveredKeys.size(), endpoints.size(), "Spring MVC 不应产生重复端点键");
        Set<EndpointKey> missingFromMatrix = new TreeSet<>(Comparator.comparing(EndpointKey::serialized));
        missingFromMatrix.addAll(discoveredKeys);
        missingFromMatrix.removeAll(matrix.keySet());
        Set<EndpointKey> staleMatrixRows = new TreeSet<>(Comparator.comparing(EndpointKey::serialized));
        staleMatrixRows.addAll(matrix.keySet());
        staleMatrixRows.removeAll(discoveredKeys);
        assertTrue(missingFromMatrix.isEmpty() && staleMatrixRows.isEmpty(), () ->
                "CSV 必须与 Spring MVC 发现的端点双向一致；缺失=" + missingFromMatrix
                        + "；失效=" + staleMatrixRows);

        Set<String> seededPermissions = readSeededPermissions();
        List<String> violations = new ArrayList<>();
        for (DiscoveredEndpoint endpoint : endpoints) {
            MatrixRow row = matrix.get(endpoint.key());
            inspect(endpoint, row, seededPermissions, violations);
        }
        assertTrue(violations.isEmpty(), () -> String.join(System.lineSeparator(), violations));
    }

    @Test
    void rejectsAnUnclassifiedMethodAndBlankMarkerReason() throws Exception {
        Method unclassified = InvalidExamples.class.getDeclaredMethod("unclassified");
        Method blankReason = InvalidExamples.class.getDeclaredMethod("blankReason");

        assertTrue(resolveClassifications(unclassified, InvalidExamples.class).isEmpty());
        List<EndpointClassification> blank = resolveClassifications(blankReason, InvalidExamples.class);
        assertEquals(1, blank.size());
        assertTrue(blank.getFirst().reason().isBlank());
    }

    private List<DiscoveredEndpoint> discoverEndpoints() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new AllControllerScanner();

        List<DiscoveredEndpoint> endpoints = new ArrayList<>();
        try (StaticWebApplicationContext context = new StaticWebApplicationContext()) {
            context.refresh();
            MappingInspector mappingInspector = new MappingInspector();
            mappingInspector.setApplicationContext(context);
            mappingInspector.afterPropertiesSet();
            for (var beanDefinition : scanner.findCandidateComponents(BASE_PACKAGE)) {
                String className = beanDefinition.getBeanClassName();
                if (className == null) {
                    continue;
                }
                Class<?> beanType = ClassUtils.forName(className, getClass().getClassLoader());
                for (Method method : ReflectionUtils.getUniqueDeclaredMethods(beanType)) {
                    RequestMappingInfo mapping = mappingInspector.mappingFor(method, beanType);
                    if (mapping == null) {
                        continue;
                    }
                    Set<String> paths = mapping.getPatternValues().isEmpty()
                            ? Set.of("")
                            : mapping.getPatternValues();
                    Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
                    if (methods.isEmpty()) {
                        for (String path : paths) {
                            endpoints.add(endpoint(beanType, method, "ANY", path));
                        }
                    } else {
                        for (RequestMethod requestMethod : methods) {
                            for (String path : paths) {
                                endpoints.add(endpoint(beanType, method, requestMethod.name(), path));
                            }
                        }
                    }
                }
            }
        }
        endpoints.sort(Comparator.comparing(endpoint -> endpoint.key().serialized()));
        return List.copyOf(endpoints);
    }

    /**
     * 权限矩阵描述的是仓库中的全部控制器端点，不能受当前测试环境的功能开关影响。
     * Spring 默认扫描器会应用 {@code @Conditional}，从而漏掉未启用的可选模块。
     */
    private static final class AllControllerScanner
            extends ClassPathScanningCandidateComponentProvider {

        private AllControllerScanner() {
            super(false);
        }

        @Override
        protected boolean isCandidateComponent(MetadataReader metadataReader) throws IOException {
            var metadata = metadataReader.getAnnotationMetadata();
            return metadata.hasAnnotation(RestController.class.getName())
                    || metadata.hasMetaAnnotation(RestController.class.getName())
                    || metadata.hasAnnotation(Controller.class.getName())
                    || metadata.hasMetaAnnotation(Controller.class.getName());
        }
    }

    private DiscoveredEndpoint endpoint(
            Class<?> beanType,
            Method method,
            String httpMethod,
            String path) {
        EndpointKey key = new EndpointKey(
                beanType.getName(), method.getName(), httpMethod, path);
        return new DiscoveredEndpoint(
                key,
                resolveClassifications(method, beanType),
                Modifier.isPublic(method.getModifiers()));
    }

    private void inspect(
            DiscoveredEndpoint endpoint,
            MatrixRow row,
            Set<String> seededPermissions,
            List<String> violations) {
        String label = endpoint.key().serialized();
        List<EndpointClassification> classifications = endpoint.classifications();
        if (classifications.isEmpty()) {
            violations.add(label + " 未声明授权分类");
            return;
        }
        if (classifications.size() > 1) {
            violations.add(label + " 声明了多个授权分类: " + classifications);
            return;
        }

        EndpointClassification actual = classifications.getFirst();
        if (actual.kind().equals("PRE_AUTHORIZE") && !endpoint.publicHandler()) {
            violations.add(label
                    + " 是受保护的 MVC Handler，必须声明为 public 以便 Method Security 代理拦截");
        }
        if (actual.reasonRequired() && actual.reason().isBlank()) {
            violations.add(label + " 的 " + actual.kind() + " reason 不能为空");
        }
        if (!COMPATIBILITY_STATUSES.contains(row.compatibilityStatus())) {
            violations.add(label + " 使用了未批准的 compatibilityStatus: "
                    + row.compatibilityStatus());
        }

        if (actual.kind().equals("PRE_AUTHORIZE")) {
            inspectProtectedClassification(label, actual, row, seededPermissions, violations);
            return;
        }

        if (!MARKER_CLASSIFICATIONS.contains(row.classification())) {
            violations.add(label + " 的 marker 与矩阵分类不一致: " + row.classification());
        }
        if (!actual.kind().equals(row.classification())) {
            violations.add(label + " 源码为 " + actual.kind() + "，CSV 为 " + row.classification());
        }
        if (!row.permissionCode().isBlank() || !row.resourceResolver().isBlank()) {
            violations.add(label + " 的 marker 行不应声明权限码或资源解析器");
        }
    }

    private void inspectProtectedClassification(
            String label,
            EndpointClassification actual,
            MatrixRow row,
            Set<String> seededPermissions,
            List<String> violations) {
        if (!PROTECTED_CLASSIFICATIONS.contains(row.classification())) {
            violations.add(label + " 的 PreAuthorize 矩阵分类无效: " + row.classification());
            return;
        }
        String expression = actual.expression();
        if ("isAuthenticated()".equals(expression)) {
            if (!"AUTHENTICATED".equals(row.classification()) || !row.permissionCode().isBlank()) {
                violations.add(label + " 应标记为 AUTHENTICATED 且不携带权限码");
            }
            return;
        }

        Matcher matcher = AUTHZ_EXPRESSION.matcher(expression);
        if (!matcher.matches()) {
            violations.add(label + " 使用了不受支持的 PreAuthorize 表达式: " + expression);
            return;
        }
        String enforcement = matcher.group(1);
        String permissionCode = matcher.group(2);
        if (permissionCode.startsWith("system:") && !"require".equals(enforcement)) {
            violations.add(label + " 的授权配置权限必须使用 @authz.require 始终强制执行");
        }
        if (!PERMISSION_CODE.matcher(permissionCode).matches()) {
            violations.add(label + " 权限编码格式非法: " + permissionCode);
        }
        if (!seededPermissions.contains(permissionCode)) {
            violations.add(label + " 权限编码未出现在 2.8.0 种子数据: " + permissionCode);
        }
        if (!permissionCode.equals(row.permissionCode())) {
            violations.add(label + " 源码权限为 " + permissionCode
                    + "，CSV 为 " + row.permissionCode());
        }
        if ("REQUIRE_RESOURCE".equals(row.classification())) {
            if (!permissionCode.startsWith("video:") || row.resourceResolver().isBlank()) {
                violations.add(label + " REQUIRE_RESOURCE 必须使用 video 权限并声明 resolver");
            }
            if ("LEGACY_AUDIT".equals(row.compatibilityStatus())) {
                violations.add(label + " REQUIRE_RESOURCE 不得继续使用 LEGACY_AUDIT");
            }
            if (!RESOURCE_RESOLVERS.contains(row.resourceResolver())) {
                violations.add(label + " 使用了未注册的资源解析器: "
                        + row.resourceResolver());
            }
        } else if (!row.resourceResolver().isBlank()) {
            violations.add(label + " 非资源分类不应声明 resolver");
        }
    }

    private List<EndpointClassification> resolveClassifications(Method method, Class<?> beanType) {
        List<EndpointClassification> methodClassifications = classificationsOn(method);
        return methodClassifications.isEmpty()
                ? classificationsOn(beanType)
                : methodClassifications;
    }

    private List<EndpointClassification> classificationsOn(AnnotatedElement element) {
        List<EndpointClassification> classifications = new ArrayList<>();
        PublicEndpoint publicEndpoint = findAnnotation(element, PublicEndpoint.class);
        if (publicEndpoint != null) {
            classifications.add(new EndpointClassification(
                    "PUBLIC", true, publicEndpoint.reason(), ""));
        }
        InternalEndpoint internalEndpoint = findAnnotation(element, InternalEndpoint.class);
        if (internalEndpoint != null) {
            classifications.add(new EndpointClassification(
                    "INTERNAL", true, internalEndpoint.reason(), ""));
        }
        MediaHookEndpoint mediaHookEndpoint = findAnnotation(element, MediaHookEndpoint.class);
        if (mediaHookEndpoint != null) {
            classifications.add(new EndpointClassification(
                    "MEDIA_HOOK", true, mediaHookEndpoint.reason(), ""));
        }
        PreAuthorize preAuthorize = findAnnotation(element, PreAuthorize.class);
        if (preAuthorize != null) {
            classifications.add(new EndpointClassification(
                    "PRE_AUTHORIZE", false, "", preAuthorize.value()));
        }
        return List.copyOf(classifications);
    }

    private <A extends Annotation> A findAnnotation(AnnotatedElement element, Class<A> annotationType) {
        return AnnotatedElementUtils.findMergedAnnotation(element, annotationType);
    }

    private Map<EndpointKey, MatrixRow> readMatrix() throws IOException {
        InputStream inputStream = getClass().getResourceAsStream(MATRIX_RESOURCE);
        assertNotNull(inputStream, MATRIX_RESOURCE + " 不存在");
        Map<EndpointKey, MatrixRow> rows = new LinkedHashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            assertEquals(
                    "handlerClass,handlerMethod,httpMethod,path,classification,permissionCode,resourceResolver,compatibilityStatus",
                    header);
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                String[] columns = line.split(",", -1);
                assertEquals(8, columns.length, "CSV 第 " + lineNumber + " 行列数错误");
                EndpointKey key = new EndpointKey(columns[0], columns[1], columns[2], columns[3]);
                MatrixRow previous = rows.put(key, new MatrixRow(
                        key, columns[4], columns[5], columns[6], columns[7]));
                assertTrue(previous == null, "CSV 重复端点: " + key.serialized());
            }
        }
        return Map.copyOf(rows);
    }

    private Set<String> readSeededPermissions() throws IOException {
        String sql = Files.readString(
                Path.of("数据库/2.8.0-h2/h2-data.sql"), StandardCharsets.UTF_8);
        Matcher matcher = SEEDED_PERMISSION.matcher(sql);
        Set<String> permissions = new HashSet<>();
        while (matcher.find()) {
            permissions.add(matcher.group(1));
        }
        assertFalse(permissions.isEmpty(), "未从 H2 种子数据读取到权限编码");
        return Set.copyOf(permissions);
    }

    private static final class MappingInspector extends RequestMappingHandlerMapping {
        RequestMappingInfo mappingFor(Method method, Class<?> handlerType) {
            return getMappingForMethod(method, handlerType);
        }
    }

    private record EndpointClassification(
            String kind,
            boolean reasonRequired,
            String reason,
            String expression) {
    }

    private record EndpointKey(
            String handlerClass,
            String handlerMethod,
            String httpMethod,
            String path) {

        String serialized() {
            return handlerClass + "#" + handlerMethod + " " + httpMethod + " " + path;
        }
    }

    private record DiscoveredEndpoint(
            EndpointKey key,
            List<EndpointClassification> classifications,
            boolean publicHandler) {
    }

    private record MatrixRow(
            EndpointKey key,
            String classification,
            String permissionCode,
            String resourceResolver,
            String compatibilityStatus) {
    }

    private static class InvalidExamples {

        @GetMapping("/unclassified")
        void unclassified() {
        }

        @PublicEndpoint(reason = " ")
        @GetMapping("/blank")
        void blankReason() {
        }
    }
}
