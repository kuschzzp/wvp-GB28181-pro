package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.LongFunction;

/**
 * 录像、告警等派生对象的控制器授权适配器。
 *
 * <p>派生对象本身不进入资源组。适配器先按业务记录主键解析其稳定来源，
 * 再按来源资源授权；详情和下载类单对象拒绝统一隐藏为 404。批量操作会先
 * 解析全部成员，再进行一次整体授权，任何成员缺失或越权都不会进入业务层。</p>
 */
@Component
public class DerivedResourceAccessAuthorizer {

    private final AuthorizationFacade authorizationFacade;
    private final ResourceResolverRegistry resolverRegistry;

    public DerivedResourceAccessAuthorizer(
            AuthorizationFacade authorizationFacade,
            ResourceResolverRegistry resolverRegistry) {
        this.authorizationFacade = authorizationFacade;
        this.resolverRegistry = resolverRegistry;
    }

    public VideoResourceRef requireRecord(
            String permissionCode,
            long recordId) {
        return requireHidden(
                permissionCode, resolverRegistry.resolveRecord(recordId));
    }

    public VideoResourceRef requireAlarm(
            String permissionCode,
            long alarmId) {
        return requireHidden(
                permissionCode, resolverRegistry.resolveAlarm(alarmId));
    }

    public void requireAllRecords(
            String permissionCode,
            Collection<Integer> recordIds) {
        requireAll(permissionCode, recordIds, "录像 ID",
                id -> resolverRegistry.resolveRecord(id));
    }

    public void requireAllAlarms(
            String permissionCode,
            Collection<Long> alarmIds) {
        requireAll(permissionCode, alarmIds, "告警 ID",
                resolverRegistry::resolveAlarm);
    }

    private VideoResourceRef requireHidden(
            String permissionCode,
            VideoResourceRef resource) {
        VideoResourceRef target = Objects.requireNonNull(
                resource, "resolved resource must not be null");
        authorizationFacade.requireVideoResource(
                permissionCode,
                target.type(),
                target.id(),
                ResourceDenialMode.HIDE_AS_NOT_FOUND);
        return target;
    }

    private <T extends Number> void requireAll(
            String permissionCode,
            Collection<T> ids,
            String fieldName,
            LongFunction<VideoResourceRef> resolver) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " 集合不能为空");
        }
        for (T id : ids) {
            if (id == null || id.longValue() <= 0) {
                throw new IllegalArgumentException(fieldName + " 必须大于零");
            }
        }
        LinkedHashSet<VideoResourceRef> resources = new LinkedHashSet<>();
        for (T id : ids) {
            resources.add(Objects.requireNonNull(
                    resolver.apply(id.longValue()),
                    "resolved resource must not be null"));
        }
        authorizationFacade.requireAllVideoResources(
                permissionCode, List.copyOf(resources));
    }
}
