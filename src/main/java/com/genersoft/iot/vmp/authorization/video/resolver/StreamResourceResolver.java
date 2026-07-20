package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.InviteInfo;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import com.genersoft.iot.vmp.service.bean.SSRCInfo;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
public class StreamResourceResolver implements ResourceResolver {

    private static final Set<VideoResourceType> SUPPORTED_TYPES = Set.copyOf(EnumSet.of(
            VideoResourceType.STREAM_PROXY,
            VideoResourceType.STREAM_PUSH,
            VideoResourceType.MEDIA_STREAM));

    private final VideoResourceLookupMapper lookupMapper;
    private final IInviteStreamService inviteStreamService;

    public StreamResourceResolver(
            VideoResourceLookupMapper lookupMapper,
            IInviteStreamService inviteStreamService) {
        this.lookupMapper = lookupMapper;
        this.inviteStreamService = inviteStreamService;
    }

    @Override
    public Set<VideoResourceType> supportedTypes() {
        return SUPPORTED_TYPES;
    }

    @Override
    public VideoResourceRef requireExisting(VideoResourceRef resource) {
        Objects.requireNonNull(resource, "resource must not be null");
        Long existingId = switch (resource.type()) {
            case STREAM_PROXY -> lookupMapper.findStreamProxyId(resource.id());
            case STREAM_PUSH -> lookupMapper.findStreamPushId(resource.id());
            case MEDIA_STREAM -> lookupMapper.findChannelId(resource.id());
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
        if (resource.type() == VideoResourceType.MEDIA_STREAM) {
            return new VideoResourceRef(VideoResourceType.CHANNEL, resource.id());
        }
        return resource;
    }

    public VideoResourceRef resolveAppStream(String app, String stream) {
        String normalizedApp = requireText(app, "应用名");
        String normalizedStream = requireText(stream, "流标识");
        Set<VideoResourceRef> candidates = new LinkedHashSet<>();

        addCandidate(candidates, VideoResourceType.STREAM_PROXY,
                lookupMapper.findStreamProxyIdByAppStream(normalizedApp, normalizedStream));
        addCandidate(candidates, VideoResourceType.STREAM_PUSH,
                lookupMapper.findStreamPushIdByAppStream(normalizedApp, normalizedStream));
        addInviteCandidates(candidates, normalizedApp, normalizedStream);

        if (candidates.isEmpty()) {
            throw new VideoResourceNotFoundException(
                    "媒体流不存在或没有可追溯来源: " + normalizedApp + "/" + normalizedStream);
        }
        if (candidates.size() > 1) {
            throw new AmbiguousVideoResourceException(
                    "媒体流匹配到多个当前来源: " + normalizedApp + "/" + normalizedStream);
        }
        return requireCandidateExisting(candidates.iterator().next());
    }

    public VideoResourceRef resolveStreamProxy(String app, String stream) {
        String normalizedApp = requireText(app, "应用名");
        String normalizedStream = requireText(stream, "流标识");
        return requireTypedStream(
                VideoResourceType.STREAM_PROXY,
                lookupMapper.findStreamProxyIdByAppStream(
                        normalizedApp, normalizedStream),
                normalizedApp,
                normalizedStream);
    }

    public VideoResourceRef resolveStreamPush(String app, String stream) {
        String normalizedApp = requireText(app, "应用名");
        String normalizedStream = requireText(stream, "流标识");
        return requireTypedStream(
                VideoResourceType.STREAM_PUSH,
                lookupMapper.findStreamPushIdByAppStream(
                        normalizedApp, normalizedStream),
                normalizedApp,
                normalizedStream);
    }

    private VideoResourceRef requireTypedStream(
            VideoResourceType type,
            Long id,
            String app,
            String stream) {
        if (id == null || id <= 0) {
            throw new VideoResourceNotFoundException(
                    "视频资源不存在: " + type + ":" + app + "/" + stream);
        }
        return requireExisting(new VideoResourceRef(type, id));
    }

    private void addInviteCandidates(
            Set<VideoResourceRef> candidates,
            String app,
            String stream) {
        List<InviteInfo> inviteInfos = inviteStreamService.getAllInviteInfo();
        if (inviteInfos == null || inviteInfos.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (InviteInfo invite : inviteInfos) {
            if (invite == null || isExpired(invite, now)) {
                continue;
            }
            StreamInfo streamInfo = invite.getStreamInfo();
            SSRCInfo ssrcInfo = invite.getSsrcInfo();
            String inviteApp = streamInfo != null && streamInfo.getApp() != null
                    ? streamInfo.getApp()
                    : ssrcInfo == null ? null : ssrcInfo.getApp();
            String inviteStream = streamInfo != null && streamInfo.getStream() != null
                    ? streamInfo.getStream()
                    : invite.getStream() != null
                            ? invite.getStream()
                            : ssrcInfo == null ? null : ssrcInfo.getStream();
            if (!app.equals(inviteApp) || !stream.equals(inviteStream)) {
                continue;
            }
            Integer channelId = streamInfo != null && streamInfo.getChannelId() != null
                    ? streamInfo.getChannelId()
                    : invite.getChannelId();
            if (channelId != null && channelId > 0) {
                candidates.add(new VideoResourceRef(VideoResourceType.CHANNEL, channelId));
            }
        }
    }

    private boolean isExpired(InviteInfo invite, long now) {
        return invite.getExpirationTime() != null && invite.getExpirationTime() <= now;
    }

    private void addCandidate(
            Set<VideoResourceRef> candidates,
            VideoResourceType type,
            Long id) {
        if (id != null && id > 0) {
            candidates.add(new VideoResourceRef(type, id));
        }
    }

    private VideoResourceRef requireCandidateExisting(VideoResourceRef candidate) {
        if (candidate.type() == VideoResourceType.CHANNEL) {
            Long channelId = lookupMapper.findChannelId(candidate.id());
            if (channelId == null || channelId <= 0) {
                throw notFound(candidate);
            }
            return candidate;
        }
        return requireExisting(candidate);
    }

    private void ensureSupported(VideoResourceType type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            throw unsupported(type);
        }
    }

    private IllegalArgumentException unsupported(VideoResourceType type) {
        return new IllegalArgumentException("流解析器不支持资源类型: " + type);
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
}
