package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Component
public class DefaultResourceResolverRegistry implements ResourceResolverRegistry {

    private final Map<VideoResourceType, ResourceResolver> resolvers;

    public DefaultResourceResolverRegistry(List<ResourceResolver> resolverList) {
        Objects.requireNonNull(resolverList, "resolverList must not be null");
        EnumMap<VideoResourceType, ResourceResolver> registered =
                new EnumMap<>(VideoResourceType.class);
        for (ResourceResolver resolver : resolverList) {
            Objects.requireNonNull(resolver, "resource resolver must not be null");
            Set<VideoResourceType> supportedTypes = Objects.requireNonNull(
                    resolver.supportedTypes(),
                    () -> resolver.getClass().getName() + " supportedTypes must not be null");
            for (VideoResourceType type : supportedTypes) {
                Objects.requireNonNull(type, "supported resource type must not be null");
                ResourceResolver previous = registered.putIfAbsent(type, resolver);
                if (previous != null) {
                    throw new IllegalStateException(
                            "资源类型存在多个解析器: " + type
                                    + " (" + previous.getClass().getName()
                                    + ", " + resolver.getClass().getName() + ")");
                }
            }
        }
        List<VideoResourceType> missingBindableTypes = List.of(VideoResourceType.values())
                .stream()
                .filter(VideoResourceType::isBindable)
                .filter(type -> !registered.containsKey(type))
                .toList();
        if (!missingBindableTypes.isEmpty()) {
            throw new IllegalStateException(
                    "可绑定资源缺少解析器: " + missingBindableTypes);
        }
        this.resolvers = Map.copyOf(registered);
    }

    @Override
    public VideoResourceRef requireExisting(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        return resolverFor(resource.type()).requireExisting(resource);
    }

    @Override
    public VideoResourceRef resolveToAuthorizable(VideoResourceRef resource) {
        VideoResourceRef existing = requireExisting(resource);
        VideoResourceRef resolved = Objects.requireNonNull(
                resolverFor(existing.type()).resolveToAuthorizable(existing),
                "resolver result must not be null");
        if (resolved.equals(existing)) {
            return existing;
        }
        return requireExisting(resolved);
    }

    @Override
    public Set<VideoResourceRef> resolveAuthorizationCandidates(VideoResourceRef resource) {
        VideoResourceRef authorizable = resolveToAuthorizable(resource);
        Set<VideoResourceRef> candidates = Objects.requireNonNull(
                resolverFor(authorizable.type()).resolveAuthorizationCandidates(authorizable),
                "resolver authorization candidates must not be null");
        if (candidates.isEmpty()) {
            throw new IllegalStateException("资源解析器没有返回授权候选项: " + authorizable);
        }
        LinkedHashSet<VideoResourceRef> verified = new LinkedHashSet<>();
        for (VideoResourceRef candidate : candidates) {
            verified.add(requireExisting(Objects.requireNonNull(
                    candidate, "authorization candidate must not be null")));
        }
        if (!verified.contains(authorizable)) {
            throw new IllegalStateException("授权候选项缺少目标资源: " + authorizable);
        }
        return Set.copyOf(verified);
    }

    @Override
    public VideoResourceRef resolveGbChannel(String deviceId, String channelId) {
        return capability(VideoResourceType.CHANNEL, ChannelResourceResolver.class)
                .resolveGbChannel(deviceId, channelId);
    }

    @Override
    public VideoResourceRef resolveGbDevice(String deviceId) {
        return capability(VideoResourceType.GB_DEVICE, ChannelResourceResolver.class)
                .resolveGbDevice(deviceId);
    }

    @Override
    public VideoResourceRef resolveUnifiedChannel(
            String channelDeviceId, String gbDeviceId) {
        return capability(VideoResourceType.CHANNEL, ChannelResourceResolver.class)
                .resolveUnifiedChannel(channelDeviceId, gbDeviceId);
    }

    @Override
    public VideoResourceRef resolveJtTerminal(String phoneNumber) {
        return capability(VideoResourceType.JT_TERMINAL, ChannelResourceResolver.class)
                .resolveJtTerminal(phoneNumber);
    }

    @Override
    public VideoResourceRef resolveJtChannel(String phoneNumber, int channelId) {
        return capability(VideoResourceType.JT_CHANNEL, ChannelResourceResolver.class)
                .resolveJtChannel(phoneNumber, channelId);
    }

    @Override
    public VideoResourceRef resolveStreamProxy(String app, String stream) {
        return capability(VideoResourceType.STREAM_PROXY, StreamResourceResolver.class)
                .resolveStreamProxy(app, stream);
    }

    @Override
    public VideoResourceRef resolveStreamPush(String app, String stream) {
        return capability(VideoResourceType.STREAM_PUSH, StreamResourceResolver.class)
                .resolveStreamPush(app, stream);
    }

    @Override
    public VideoResourceRef resolveAppStream(String app, String stream) {
        return capability(VideoResourceType.MEDIA_STREAM, StreamResourceResolver.class)
                .resolveAppStream(app, stream);
    }

    @Override
    public VideoResourceRef resolveRecord(long recordId) {
        return resolveToAuthorizable(
                new VideoResourceRef(VideoResourceType.CLOUD_RECORD, recordId));
    }

    @Override
    public VideoResourceRef resolveAlarm(long alarmId) {
        return resolveToAuthorizable(
                new VideoResourceRef(VideoResourceType.ALARM, alarmId));
    }

    private ResourceResolver resolverFor(VideoResourceType type) {
        ResourceResolver resolver = resolvers.get(type);
        if (resolver == null) {
            throw new IllegalStateException("资源类型没有注册解析器: " + type);
        }
        return resolver;
    }

    private <T extends ResourceResolver> T capability(
            VideoResourceType type,
            Class<T> capabilityType) {
        ResourceResolver resolver = resolverFor(type);
        if (!capabilityType.isInstance(resolver)) {
            throw new IllegalStateException(
                    "资源解析器缺少能力: " + type + " -> " + capabilityType.getSimpleName());
        }
        return capabilityType.cast(resolver);
    }
}
