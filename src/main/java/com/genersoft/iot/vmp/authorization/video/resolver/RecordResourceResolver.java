package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Component
public class RecordResourceResolver implements ResourceResolver {

    private static final Set<VideoResourceType> SUPPORTED_TYPES = Set.copyOf(EnumSet.of(
            VideoResourceType.CLOUD_RECORD,
            VideoResourceType.DEVICE_RECORD,
            VideoResourceType.LEGACY_UNBOUND));

    private final VideoResourceLookupMapper lookupMapper;

    public RecordResourceResolver(VideoResourceLookupMapper lookupMapper) {
        this.lookupMapper = lookupMapper;
    }

    @Override
    public Set<VideoResourceType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public VideoResourceRef requireExisting(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        return switch (resource.type()) {
            case CLOUD_RECORD -> {
                requireRecord(resource.id());
                yield resource;
            }
            case DEVICE_RECORD -> {
                requireChannel(resource.id());
                yield resource;
            }
            case LEGACY_UNBOUND -> {
                CloudRecordSource source = requireRecord(resource.id());
                if (source.channelId() != null
                        || sourceType(source.sourceType()) != VideoResourceType.LEGACY_UNBOUND) {
                    throw notFound(resource);
                }
                yield resource;
            }
            default -> throw unsupported(resource.type());
        };
    }

    @Override
    public VideoResourceRef resolveToAuthorizable(VideoResourceRef resource) {
        ensureSupported(resource.type());
        return switch (resource.type()) {
            case CLOUD_RECORD -> resolveCloudRecord(resource.id());
            case DEVICE_RECORD -> new VideoResourceRef(VideoResourceType.CHANNEL, resource.id());
            case LEGACY_UNBOUND -> resource;
            default -> throw unsupported(resource.type());
        };
    }

    private VideoResourceRef resolveCloudRecord(long recordId) {
        CloudRecordSource source = requireRecord(recordId);
        if (source.channelId() != null && source.channelId() > 0) {
            return new VideoResourceRef(VideoResourceType.CHANNEL, source.channelId());
        }
        VideoResourceType sourceType = sourceType(source.sourceType());
        if (sourceType == VideoResourceType.LEGACY_UNBOUND) {
            return new VideoResourceRef(VideoResourceType.LEGACY_UNBOUND, recordId);
        }
        if (!sourceType.isBindable()) {
            throw new IllegalStateException(
                    "云录像来源类型不可授权: " + sourceType + ", recordId=" + recordId);
        }
        if (source.sourceId() == null || source.sourceId() <= 0) {
            throw new IllegalStateException(
                    "云录像缺少稳定来源主键: recordId=" + recordId);
        }
        return new VideoResourceRef(sourceType, source.sourceId());
    }

    private CloudRecordSource requireRecord(long recordId) {
        CloudRecordSource source = lookupMapper.findCloudRecordSource(recordId);
        if (source == null) {
            throw new VideoResourceNotFoundException("云录像不存在: " + recordId);
        }
        return source;
    }

    private void requireChannel(long channelId) {
        Long existingId = lookupMapper.findChannelId(channelId);
        if (existingId == null || existingId <= 0) {
            throw new VideoResourceNotFoundException("录像来源通道不存在: " + channelId);
        }
    }

    private VideoResourceType sourceType(String value) {
        if (value == null || value.isBlank()) {
            return VideoResourceType.LEGACY_UNBOUND;
        }
        try {
            return VideoResourceType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("未知云录像来源类型: " + value, exception);
        }
    }

    private void ensureSupported(VideoResourceType type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            throw unsupported(type);
        }
    }

    private IllegalArgumentException unsupported(VideoResourceType type) {
        return new IllegalArgumentException("录像解析器不支持资源类型: " + type);
    }

    private VideoResourceNotFoundException notFound(VideoResourceRef resource) {
        return new VideoResourceNotFoundException(
                "视频资源不存在: " + resource.type() + ":" + resource.id());
    }
}
