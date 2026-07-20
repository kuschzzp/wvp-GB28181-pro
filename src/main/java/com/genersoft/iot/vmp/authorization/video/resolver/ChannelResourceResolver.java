package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.enums.ChannelDataType;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class ChannelResourceResolver implements ResourceResolver {

    private static final Set<VideoResourceType> SUPPORTED_TYPES = Set.copyOf(EnumSet.of(
            VideoResourceType.GB_DEVICE,
            VideoResourceType.CHANNEL,
            VideoResourceType.JT_TERMINAL,
            VideoResourceType.JT_CHANNEL));

    private final VideoResourceLookupMapper lookupMapper;

    public ChannelResourceResolver(VideoResourceLookupMapper lookupMapper) {
        this.lookupMapper = lookupMapper;
    }

    @Override
    public Set<VideoResourceType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public VideoResourceRef requireExisting(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        Long existingId = switch (resource.type()) {
            case GB_DEVICE -> lookupMapper.findGbDeviceId(resource.id());
            case CHANNEL -> lookupMapper.findChannelId(resource.id());
            case JT_TERMINAL -> lookupMapper.findJtTerminalId(resource.id());
            case JT_CHANNEL -> lookupMapper.findJtChannelId(resource.id());
            default -> throw unsupported(resource.type());
        };
        if (existingId == null || existingId <= 0) {
            throw notFound(resource);
        }
        return resource;
    }

    @Override
    public VideoResourceRef resolveToAuthorizable(VideoResourceRef resource) {
        ensureSupported(resource.type());
        return resource;
    }

    @Override
    public Set<VideoResourceRef> resolveAuthorizationCandidates(VideoResourceRef resource) {
        VideoResourceRef existing = requireExisting(resource);
        LinkedHashSet<VideoResourceRef> candidates = new LinkedHashSet<>();
        candidates.add(existing);
        if (existing.type() == VideoResourceType.CHANNEL) {
            addUnifiedChannelSources(existing.id(), candidates);
        } else if (existing.type() == VideoResourceType.JT_CHANNEL) {
            addJtTerminal(existing.id(), candidates);
        }
        return Set.copyOf(candidates);
    }

    public VideoResourceRef resolveGbChannel(String deviceId, String channelId) {
        String normalizedDeviceId = requireText(deviceId, "设备编号");
        String normalizedChannelId = requireText(channelId, "通道编号");
        List<Long> matches = lookupMapper.findGbChannelIds(
                normalizedDeviceId, normalizedChannelId);
        List<Long> distinctIds = matches == null
                ? List.of()
                : matches.stream().filter(Objects::nonNull).filter(id -> id > 0).distinct().toList();
        if (distinctIds.isEmpty()) {
            throw new VideoResourceNotFoundException(
                    "国标通道不存在: " + normalizedDeviceId + "/" + normalizedChannelId);
        }
        if (distinctIds.size() > 1) {
            throw new AmbiguousVideoResourceException(
                    "国标通道匹配到多个内部资源: " + normalizedDeviceId + "/" + normalizedChannelId);
        }
        return new VideoResourceRef(VideoResourceType.CHANNEL, distinctIds.getFirst());
    }

    public VideoResourceRef resolveGbDevice(String deviceId) {
        String normalizedDeviceId = requireText(deviceId, "设备编号");
        List<Long> matches = lookupMapper.findGbDeviceIdsByDeviceId(
                normalizedDeviceId);
        List<Long> distinctIds = matches == null
                ? List.of()
                : matches.stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .distinct()
                        .toList();
        if (distinctIds.isEmpty()) {
            throw new VideoResourceNotFoundException(
                    "国标设备不存在: " + normalizedDeviceId);
        }
        if (distinctIds.size() > 1) {
            throw new AmbiguousVideoResourceException(
                    "国标设备编号匹配到多个内部资源: " + normalizedDeviceId);
        }
        return new VideoResourceRef(
                VideoResourceType.GB_DEVICE, distinctIds.getFirst());
    }

    public VideoResourceRef resolveUnifiedChannel(
            String channelDeviceId, String gbDeviceId) {
        String normalizedChannelDeviceId = requireText(
                channelDeviceId, "通道编号");
        String normalizedGbDeviceId = normalizeOptionalText(gbDeviceId);
        List<Long> matches = lookupMapper.findUnifiedChannelIds(
                normalizedChannelDeviceId, normalizedGbDeviceId);
        List<Long> distinctIds = matches == null
                ? List.of()
                : matches.stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .distinct()
                        .toList();
        if (distinctIds.isEmpty()) {
            throw new VideoResourceNotFoundException(
                    "统一通道不存在: " + normalizedChannelDeviceId);
        }
        if (distinctIds.size() > 1) {
            throw new AmbiguousVideoResourceException(
                    "统一通道匹配到多个内部资源: "
                            + normalizedChannelDeviceId);
        }
        return new VideoResourceRef(
                VideoResourceType.CHANNEL, distinctIds.getFirst());
    }

    public VideoResourceRef resolveJtTerminal(String phoneNumber) {
        String normalizedPhoneNumber = requireText(phoneNumber, "终端手机号");
        return requireUniqueExternalResource(
                VideoResourceType.JT_TERMINAL,
                lookupMapper.findJtTerminalIdsByPhoneNumber(normalizedPhoneNumber),
                "JT1078 终端不存在: " + normalizedPhoneNumber,
                "JT1078 终端手机号匹配到多个内部资源: " + normalizedPhoneNumber);
    }

    public VideoResourceRef resolveJtChannel(String phoneNumber, int channelId) {
        String normalizedPhoneNumber = requireText(phoneNumber, "终端手机号");
        if (channelId <= 0) {
            throw new VideoResourceNotFoundException(
                    "JT1078 通道不存在: " + normalizedPhoneNumber + "/" + channelId);
        }
        return requireUniqueExternalResource(
                VideoResourceType.JT_CHANNEL,
                lookupMapper.findJtChannelIdsByPhoneNumberAndChannelId(
                        normalizedPhoneNumber, channelId),
                "JT1078 通道不存在: " + normalizedPhoneNumber + "/" + channelId,
                "JT1078 通道匹配到多个内部资源: "
                        + normalizedPhoneNumber + "/" + channelId);
    }

    private VideoResourceRef requireUniqueExternalResource(
            VideoResourceType type,
            List<Long> matches,
            String notFoundMessage,
            String ambiguousMessage) {
        List<Long> distinctIds = matches == null
                ? List.of()
                : matches.stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .distinct()
                        .toList();
        if (distinctIds.isEmpty()) {
            throw new VideoResourceNotFoundException(notFoundMessage);
        }
        if (distinctIds.size() > 1) {
            throw new AmbiguousVideoResourceException(ambiguousMessage);
        }
        return new VideoResourceRef(type, distinctIds.getFirst());
    }

    private void addUnifiedChannelSources(
            long channelId,
            Set<VideoResourceRef> candidates) {
        ChannelAuthorizationSource source = lookupMapper.findChannelAuthorizationSource(channelId);
        if (source == null || source.dataDeviceId() <= 0) {
            return;
        }
        switch (source.dataType()) {
            case ChannelDataType.GB28181 -> addIfExisting(
                    candidates, VideoResourceType.GB_DEVICE, source.dataDeviceId());
            case ChannelDataType.STREAM_PUSH -> addIfExisting(
                    candidates, VideoResourceType.STREAM_PUSH, source.dataDeviceId());
            case ChannelDataType.STREAM_PROXY -> addIfExisting(
                    candidates, VideoResourceType.STREAM_PROXY, source.dataDeviceId());
            case ChannelDataType.JT_1078 -> {
                addIfExisting(candidates, VideoResourceType.JT_CHANNEL, source.dataDeviceId());
                addIfExisting(candidates, VideoResourceType.JT_TERMINAL, source.jtTerminalId());
            }
            default -> {
                // Unknown source types can only use explicit CHANNEL grants.
            }
        }
    }

    private void addJtTerminal(long jtChannelId, Set<VideoResourceRef> candidates) {
        addIfExisting(
                candidates,
                VideoResourceType.JT_TERMINAL,
                lookupMapper.findJtTerminalIdByChannelId(jtChannelId));
    }

    private void addIfExisting(
            Set<VideoResourceRef> candidates,
            VideoResourceType type,
            Long id) {
        if (id == null || id <= 0) {
            return;
        }
        Long existingId = switch (type) {
            case GB_DEVICE -> lookupMapper.findGbDeviceId(id);
            case JT_TERMINAL -> lookupMapper.findJtTerminalId(id);
            case JT_CHANNEL -> lookupMapper.findJtChannelId(id);
            case STREAM_PROXY -> lookupMapper.findStreamProxyId(id);
            case STREAM_PUSH -> lookupMapper.findStreamPushId(id);
            default -> null;
        };
        if (existingId != null && existingId > 0) {
            candidates.add(new VideoResourceRef(type, existingId));
        }
    }

    private void ensureSupported(VideoResourceType type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            throw unsupported(type);
        }
    }

    private IllegalArgumentException unsupported(VideoResourceType type) {
        return new IllegalArgumentException("通道解析器不支持资源类型: " + type);
    }

    private VideoResourceNotFoundException notFound(VideoResourceRef resource) {
        return new VideoResourceNotFoundException(
                "视频资源不存在: " + resource.type() + ":" + resource.id());
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private String normalizeOptionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
