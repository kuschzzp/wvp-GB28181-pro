package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.common.enums.MediaStreamUtil;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * GB28181 控制器的对象授权适配器。
 *
 * <p>控制器只提供其已有的外部设备/通道编号、统一通道主键或当前 RTP
 * stream；本适配器负责转换为稳定内部资源后交给统一授权门面。解析失败、
 * 歧义和资源拒绝均原样抛出，业务命令不得先执行。</p>
 */
@Component
public class GbChannelAccessAuthorizer {

    private final AuthorizationFacade authorizationFacade;
    private final ResourceResolverRegistry resolverRegistry;

    public GbChannelAccessAuthorizer(
            AuthorizationFacade authorizationFacade,
            ResourceResolverRegistry resolverRegistry) {
        this.authorizationFacade = authorizationFacade;
        this.resolverRegistry = resolverRegistry;
    }

    public VideoResourceRef requireGbChannel(
            String permissionCode,
            String deviceId,
            String channelId) {
        return require(permissionCode,
                resolverRegistry.resolveGbChannel(deviceId, channelId));
    }

    public VideoResourceRef requireGbDevice(
            String permissionCode,
            String deviceId) {
        return require(permissionCode,
                resolverRegistry.resolveGbDevice(deviceId));
    }

    public VideoResourceRef requireGbDeviceHidden(
            String permissionCode,
            String deviceId) {
        return requireHidden(permissionCode,
                resolverRegistry.resolveGbDevice(deviceId));
    }

    public VideoResourceRef requireGbDeviceId(
            String permissionCode,
            long deviceId) {
        return require(permissionCode, gbDevice(deviceId));
    }

    public VideoResourceRef requireGbDeviceIdHidden(
            String permissionCode,
            long deviceId) {
        return requireHidden(permissionCode, gbDevice(deviceId));
    }

    public VideoResourceRef requireGbChannelHidden(
            String permissionCode,
            String deviceId,
            String channelId) {
        return requireHidden(permissionCode,
                resolverRegistry.resolveGbChannel(deviceId, channelId));
    }

    public VideoResourceRef requireUnifiedChannel(
            String permissionCode,
            String channelDeviceId,
            String gbDeviceId) {
        return require(permissionCode,
                resolverRegistry.resolveUnifiedChannel(channelDeviceId, gbDeviceId));
    }

    public VideoResourceRef requireUnifiedChannelHidden(
            String permissionCode,
            String channelDeviceId,
            String gbDeviceId) {
        return requireHidden(permissionCode,
                resolverRegistry.resolveUnifiedChannel(
                        channelDeviceId, gbDeviceId));
    }

    public VideoResourceRef requireChannel(
            String permissionCode,
            long channelId) {
        return require(permissionCode,
                new VideoResourceRef(VideoResourceType.CHANNEL, channelId));
    }

    public VideoResourceRef requireChannelHidden(
            String permissionCode,
            long channelId) {
        return requireHidden(permissionCode,
                new VideoResourceRef(VideoResourceType.CHANNEL, channelId));
    }

    public void requireAllChannels(
            String permissionCode,
            Collection<Integer> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            throw new IllegalArgumentException("通道 ID 集合不能为空");
        }
        for (Integer channelId : channelIds) {
            if (channelId == null || channelId <= 0) {
                throw new IllegalArgumentException("通道 ID 必须大于零");
            }
        }
        LinkedHashSet<VideoResourceRef> resources = new LinkedHashSet<>();
        for (Integer channelId : channelIds) {
            resources.add(new VideoResourceRef(
                    VideoResourceType.CHANNEL, channelId.longValue()));
        }
        authorizationFacade.requireAllVideoResources(
                permissionCode, List.copyOf(resources));
    }

    public void requireAllGbDevices(
            String permissionCode,
            Collection<Integer> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            throw new IllegalArgumentException("设备 ID 集合不能为空");
        }
        LinkedHashSet<VideoResourceRef> resources = new LinkedHashSet<>();
        for (Integer deviceId : deviceIds) {
            if (deviceId == null) {
                throw new IllegalArgumentException("设备 ID 不能为空");
            }
            resources.add(gbDevice(deviceId.longValue()));
        }
        authorizationFacade.requireAllVideoResources(
                permissionCode, List.copyOf(resources));
    }

    public VideoResourceRef requireRtpStream(
            String permissionCode,
            String stream) {
        return require(permissionCode,
                resolverRegistry.resolveAppStream(MediaStreamUtil.RTP_APP, stream));
    }

    public VideoResourceRef requireAppStream(
            String permissionCode,
            String app,
            String stream) {
        return require(permissionCode,
                resolverRegistry.resolveAppStream(app, stream));
    }

    private VideoResourceRef gbDevice(long deviceId) {
        if (deviceId <= 0) {
            throw new IllegalArgumentException("设备 ID 必须大于零");
        }
        return new VideoResourceRef(VideoResourceType.GB_DEVICE, deviceId);
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
}
