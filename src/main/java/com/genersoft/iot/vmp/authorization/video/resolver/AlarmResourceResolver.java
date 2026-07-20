package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Set;

@Component
public class AlarmResourceResolver implements ResourceResolver {

    private static final Set<VideoResourceType> SUPPORTED_TYPES = Set.of(VideoResourceType.ALARM);

    private final VideoResourceLookupMapper lookupMapper;

    public AlarmResourceResolver(VideoResourceLookupMapper lookupMapper) {
        this.lookupMapper = lookupMapper;
    }

    @Override
    public Set<VideoResourceType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public VideoResourceRef requireExisting(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        ensureSupported(resource.type());
        requireChannelId(resource.id());
        return resource;
    }

    @Override
    public VideoResourceRef resolveToAuthorizable(VideoResourceRef resource) {
        ensureSupported(resource.type());
        return new VideoResourceRef(
                VideoResourceType.CHANNEL,
                requireChannelId(resource.id()));
    }

    private long requireChannelId(long alarmId) {
        Long channelId = lookupMapper.findAlarmChannelId(alarmId);
        if (channelId == null || channelId <= 0) {
            throw new VideoResourceNotFoundException(
                    "告警不存在或没有可追溯通道: " + alarmId);
        }
        return channelId;
    }

    private void ensureSupported(VideoResourceType type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            throw new IllegalArgumentException("告警解析器不支持资源类型: " + type);
        }
    }
}
