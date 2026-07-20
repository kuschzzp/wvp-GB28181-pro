package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * 拉流代理与推流管理接口的对象授权适配器。
 */
@Component
public class StreamResourceAccessAuthorizer {

    private final AuthorizationFacade authorizationFacade;
    private final ResourceResolverRegistry resolverRegistry;

    public StreamResourceAccessAuthorizer(
            AuthorizationFacade authorizationFacade,
            ResourceResolverRegistry resolverRegistry) {
        this.authorizationFacade = authorizationFacade;
        this.resolverRegistry = resolverRegistry;
    }

    public VideoResourceRef requireProxy(
            String permissionCode,
            String app,
            String stream) {
        return require(permissionCode,
                resolverRegistry.resolveStreamProxy(app, stream));
    }

    public VideoResourceRef requirePush(
            String permissionCode,
            String app,
            String stream) {
        return require(permissionCode,
                resolverRegistry.resolveStreamPush(app, stream));
    }

    public VideoResourceRef requireAppStream(
            String permissionCode,
            String app,
            String stream) {
        return require(permissionCode,
                resolverRegistry.resolveAppStream(app, stream));
    }

    public VideoResourceRef requireProxyId(
            String permissionCode,
            long proxyId) {
        return require(permissionCode,
                new VideoResourceRef(VideoResourceType.STREAM_PROXY, proxyId));
    }

    public VideoResourceRef requirePushId(
            String permissionCode,
            long pushId) {
        return require(permissionCode,
                new VideoResourceRef(VideoResourceType.STREAM_PUSH, pushId));
    }

    public void requireAllPushIds(
            String permissionCode,
            Collection<Integer> pushIds) {
        Objects.requireNonNull(pushIds, "pushIds must not be null");
        LinkedHashSet<VideoResourceRef> resources = new LinkedHashSet<>();
        for (Integer pushId : pushIds) {
            if (pushId == null) {
                throw new IllegalArgumentException("推流 ID 不能为空");
            }
            resources.add(new VideoResourceRef(
                    VideoResourceType.STREAM_PUSH, pushId.longValue()));
        }
        if (resources.isEmpty()) {
            throw new IllegalArgumentException("推流 ID 集合不能为空");
        }
        authorizationFacade.requireAllVideoResources(
                permissionCode, List.copyOf(resources));
    }

    private VideoResourceRef require(
            String permissionCode,
            VideoResourceRef resource) {
        VideoResourceRef target = Objects.requireNonNull(
                resource, "resolved resource must not be null");
        authorizationFacade.requireVideoResource(
                permissionCode, target.type(), target.id());
        return target;
    }
}
