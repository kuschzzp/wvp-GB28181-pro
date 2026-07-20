package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.gb28181.bean.SsrcTransaction;
import com.genersoft.iot.vmp.gb28181.session.SipInviteSessionManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
public class DefaultCloudRecordSourceBindingResolver
        implements CloudRecordSourceBindingResolver {

    private static final int STREAM_PUSH_DATA_TYPE = 2;
    private static final int STREAM_PROXY_DATA_TYPE = 3;

    private final SipInviteSessionManager sessionManager;
    private final ResourceResolverRegistry resourceResolverRegistry;
    private final VideoResourceLookupMapper lookupMapper;

    public DefaultCloudRecordSourceBindingResolver(
            SipInviteSessionManager sessionManager,
            ResourceResolverRegistry resourceResolverRegistry,
            VideoResourceLookupMapper lookupMapper) {
        this.sessionManager = Objects.requireNonNull(
                sessionManager, "sessionManager must not be null");
        this.resourceResolverRegistry = Objects.requireNonNull(
                resourceResolverRegistry,
                "resourceResolverRegistry must not be null");
        this.lookupMapper = Objects.requireNonNull(
                lookupMapper, "lookupMapper must not be null");
    }

    @Override
    public CloudRecordSourceBinding resolveForNewRecord(
            String callId,
            String app,
            String stream) {
        String normalizedApp = requireText(app, "应用名");
        String normalizedStream = requireText(stream, "流标识");

        Optional<CloudRecordSourceBinding> byCallId =
                findByCallIdSession(callId);
        if (byCallId.isPresent()) {
            return byCallId.get();
        }

        Optional<CloudRecordSourceBinding> bySession =
                findByAppStreamSession(normalizedApp, normalizedStream);
        if (bySession.isPresent()) {
            return bySession.get();
        }

        return findByUnifiedMapping(normalizedApp, normalizedStream)
                .orElseThrow(() -> new CloudRecordSourceResolutionException(
                        "云录像没有可追溯的唯一来源: "
                                + normalizedApp + "/" + normalizedStream));
    }

    @Override
    public Optional<CloudRecordSourceBinding> findByCallIdSession(String callId) {
        if (!hasText(callId)) {
            return Optional.empty();
        }
        SsrcTransaction transaction =
                sessionManager.getSsrcTransactionByCallId(callId.trim());
        if (transaction == null) {
            return Optional.empty();
        }
        return Optional.of(bindingForTransaction(
                transaction, "callId=" + callId.trim()));
    }

    @Override
    public Optional<CloudRecordSourceBinding> findByAppStreamSession(
            String app,
            String stream) {
        if (!hasText(app) || !hasText(stream)) {
            return Optional.empty();
        }
        String normalizedApp = app.trim();
        String normalizedStream = stream.trim();
        SsrcTransaction transaction = sessionManager.getSsrcTransactionByStream(
                normalizedApp, normalizedStream);
        if (transaction == null) {
            return Optional.empty();
        }
        return Optional.of(bindingForTransaction(
                transaction,
                "app/stream=" + normalizedApp + "/" + normalizedStream));
    }

    @Override
    public Optional<CloudRecordSourceBinding> findByUnifiedMapping(
            String app,
            String stream) {
        if (!hasText(app) || !hasText(stream)) {
            return Optional.empty();
        }
        String normalizedApp = app.trim();
        String normalizedStream = stream.trim();
        try {
            VideoResourceRef resource = resourceResolverRegistry.resolveAppStream(
                    normalizedApp, normalizedStream);
            return Optional.of(bindingForResource(resource));
        } catch (VideoResourceNotFoundException e) {
            return Optional.empty();
        } catch (CloudRecordSourceResolutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new CloudRecordSourceResolutionException(
                    "云录像来源解析失败: "
                            + normalizedApp + "/" + normalizedStream,
                    e);
        }
    }

    private CloudRecordSourceBinding bindingForTransaction(
            SsrcTransaction transaction,
            String context) {
        Integer channelId = transaction.getChannelId();
        if (channelId == null || channelId <= 0) {
            throw new CloudRecordSourceResolutionException(
                    "会话缺少有效统一通道: " + context);
        }
        return channelBinding(channelId, context);
    }

    private CloudRecordSourceBinding bindingForResource(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resolved resource must not be null");
        return switch (resource.type()) {
            case CHANNEL -> channelBinding(
                    resource.id(), "统一流映射=" + resource);
            case STREAM_PUSH -> sourceBinding(
                    resource, STREAM_PUSH_DATA_TYPE);
            case STREAM_PROXY -> sourceBinding(
                    resource, STREAM_PROXY_DATA_TYPE);
            default -> throw new CloudRecordSourceResolutionException(
                    "云录像来源不是可固化类型: " + resource);
        };
    }

    private CloudRecordSourceBinding channelBinding(long channelId, String context) {
        Long existingChannelId = lookupMapper.findChannelId(channelId);
        if (existingChannelId == null || existingChannelId <= 0) {
            throw new CloudRecordSourceResolutionException(
                    "云录像来源通道不存在: " + channelId + " (" + context + ")");
        }
        return new CloudRecordSourceBinding(
                VideoResourceType.CHANNEL,
                existingChannelId,
                existingChannelId);
    }

    private CloudRecordSourceBinding sourceBinding(
            VideoResourceRef source,
            int dataType) {
        List<Long> channelIds = lookupMapper.findChannelIdsBySource(
                dataType, source.id());
        List<Long> uniqueChannelIds = channelIds == null
                ? List.of()
                : channelIds.stream()
                        .filter(Objects::nonNull)
                        .filter(id -> id > 0)
                        .distinct()
                        .toList();
        if (uniqueChannelIds.size() != 1) {
            throw new CloudRecordSourceResolutionException(
                    "云录像来源必须映射到唯一统一通道: "
                            + source + ", 候选数=" + uniqueChannelIds.size());
        }
        return new CloudRecordSourceBinding(
                source.type(), source.id(), uniqueChannelIds.get(0));
    }

    private String requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new CloudRecordSourceResolutionException(fieldName + "不能为空");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
