package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * JT1078 HTTP 接口的对象授权适配器。
 *
 * <p>外部手机号/通道号必须先唯一解析成内部主键；仅有内部主键的管理接口
 * 直接按稳定资源类型授权。任何解析或授权异常都在 JT 命令与媒体副作用前抛出。</p>
 */
@Component
public class JtResourceAccessAuthorizer {

    private final AuthorizationFacade authorizationFacade;
    private final ResourceResolverRegistry resolverRegistry;

    public JtResourceAccessAuthorizer(
            AuthorizationFacade authorizationFacade,
            ResourceResolverRegistry resolverRegistry) {
        this.authorizationFacade = authorizationFacade;
        this.resolverRegistry = resolverRegistry;
    }

    public VideoResourceRef requireTerminal(
            String permissionCode,
            String phoneNumber) {
        return require(permissionCode,
                resolverRegistry.resolveJtTerminal(phoneNumber));
    }

    public VideoResourceRef requireChannel(
            String permissionCode,
            String phoneNumber,
            int channelId) {
        return require(permissionCode,
                resolverRegistry.resolveJtChannel(phoneNumber, channelId));
    }

    public VideoResourceRef requireChannelOrTerminal(
            String permissionCode,
            String phoneNumber,
            Integer channelId) {
        if (channelId == null || channelId < 0) {
            throw new IllegalArgumentException("JT1078 通道编号不能为空或小于零");
        }
        return channelId == 0
                ? requireTerminal(permissionCode, phoneNumber)
                : requireChannel(permissionCode, phoneNumber, channelId);
    }

    public VideoResourceRef requireTerminalId(
            String permissionCode,
            long terminalId) {
        return require(permissionCode,
                new VideoResourceRef(VideoResourceType.JT_TERMINAL, terminalId));
    }

    public VideoResourceRef requireChannelId(
            String permissionCode,
            long channelId) {
        return require(permissionCode,
                new VideoResourceRef(VideoResourceType.JT_CHANNEL, channelId));
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
