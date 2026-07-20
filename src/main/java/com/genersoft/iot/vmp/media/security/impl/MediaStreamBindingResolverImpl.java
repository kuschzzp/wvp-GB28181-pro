package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.resolver.AmbiguousVideoResourceException;
import com.genersoft.iot.vmp.authorization.video.resolver.ChannelAuthorizationSource;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceLookupMapper;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.common.InviteInfo;
import com.genersoft.iot.vmp.common.InviteSessionType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.common.VideoManagerConstants;
import com.genersoft.iot.vmp.common.enums.ChannelDataType;
import com.genersoft.iot.vmp.gb28181.service.IInviteStreamService;
import com.genersoft.iot.vmp.jt1078.bean.JTChannel;
import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import com.genersoft.iot.vmp.jt1078.dao.JTChannelMapper;
import com.genersoft.iot.vmp.jt1078.dao.JTTerminalMapper;
import com.genersoft.iot.vmp.media.bean.MediaInfo;
import com.genersoft.iot.vmp.media.security.ActiveMediaBindingRegistry;
import com.genersoft.iot.vmp.media.security.MediaStreamBinding;
import com.genersoft.iot.vmp.media.security.MediaStreamBindingResolver;
import com.genersoft.iot.vmp.service.bean.SSRCInfo;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.streamProxy.bean.StreamProxy;
import com.genersoft.iot.vmp.streamProxy.dao.StreamProxyMapper;
import com.genersoft.iot.vmp.streamPush.bean.StreamPush;
import com.genersoft.iot.vmp.streamPush.dao.StreamPushMapper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

@Service
public class MediaStreamBindingResolverImpl implements MediaStreamBindingResolver {

    private final ActiveMediaBindingRegistry bindingRegistry;
    private final IRedisCatchStorage redisCatchStorage;
    private final IInviteStreamService inviteStreamService;
    private final VideoResourceLookupMapper resourceLookupMapper;
    private final StreamProxyMapper streamProxyMapper;
    private final StreamPushMapper streamPushMapper;
    private final JTChannelMapper jtChannelMapper;
    private final JTTerminalMapper jtTerminalMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    public MediaStreamBindingResolverImpl(
            ActiveMediaBindingRegistry bindingRegistry,
            IRedisCatchStorage redisCatchStorage,
            IInviteStreamService inviteStreamService,
            VideoResourceLookupMapper resourceLookupMapper,
            StreamProxyMapper streamProxyMapper,
            StreamPushMapper streamPushMapper,
            JTChannelMapper jtChannelMapper,
            JTTerminalMapper jtTerminalMapper,
            RedisTemplate<String, Object> redisTemplate) {
        this.bindingRegistry = Objects.requireNonNull(
                bindingRegistry, "bindingRegistry must not be null");
        this.redisCatchStorage = Objects.requireNonNull(
                redisCatchStorage, "redisCatchStorage must not be null");
        this.inviteStreamService = Objects.requireNonNull(
                inviteStreamService, "inviteStreamService must not be null");
        this.resourceLookupMapper = Objects.requireNonNull(
                resourceLookupMapper, "resourceLookupMapper must not be null");
        this.streamProxyMapper = Objects.requireNonNull(
                streamProxyMapper, "streamProxyMapper must not be null");
        this.streamPushMapper = Objects.requireNonNull(
                streamPushMapper, "streamPushMapper must not be null");
        this.jtChannelMapper = Objects.requireNonNull(
                jtChannelMapper, "jtChannelMapper must not be null");
        this.jtTerminalMapper = Objects.requireNonNull(
                jtTerminalMapper, "jtTerminalMapper must not be null");
        this.redisTemplate = Objects.requireNonNull(
                redisTemplate, "redisTemplate must not be null");
    }

    @Override
    public MediaStreamBinding resolve(VideoResourceType type, long resourceId) {
        Objects.requireNonNull(type, "resource type must not be null");
        if (resourceId <= 0) {
            throw new IllegalArgumentException("resourceId must be positive");
        }
        Optional<MediaStreamBinding> registered = bindingRegistry.find(type, resourceId);
        if (registered.isPresent()) {
            MediaStreamBinding binding = registered.get();
            if (isActive(binding)) {
                return binding;
            }
            bindingRegistry.remove(type, resourceId);
        }

        return switch (type) {
            case CHANNEL -> resolveChannel(resourceId);
            case DEVICE_RECORD -> resolveInvite(
                    InviteSessionType.PLAYBACK, resourceId, type, resourceId);
            case JT_CHANNEL -> resolveJtChannel(resourceId, type, resourceId, false);
            case STREAM_PROXY -> resolveProxy(resourceId, type, resourceId);
            case STREAM_PUSH -> resolvePush(resourceId, type, resourceId);
            case GB_DEVICE, JT_TERMINAL -> throw new AmbiguousVideoResourceException(
                    "设备级资源不能唯一确定活动媒体流，请使用具体通道资源: "
                            + type + ":" + resourceId);
            case CLOUD_RECORD, AUDIO_TALK -> throw inactive(type, resourceId);
            case ALARM, MEDIA_STREAM, LEGACY_UNBOUND -> throw new IllegalArgumentException(
                    "资源类型不支持签发播放票据: " + type);
        };
    }

    private MediaStreamBinding resolveChannel(long channelId) {
        ChannelAuthorizationSource source =
                resourceLookupMapper.findChannelAuthorizationSource(channelId);
        if (source == null) {
            throw inactive(VideoResourceType.CHANNEL, channelId);
        }
        return switch (source.dataType()) {
            case ChannelDataType.GB28181 -> resolveInvite(
                    InviteSessionType.PLAY,
                    channelId,
                    VideoResourceType.CHANNEL,
                    channelId);
            case ChannelDataType.STREAM_PROXY -> resolveProxy(
                    source.dataDeviceId(), VideoResourceType.CHANNEL, channelId);
            case ChannelDataType.STREAM_PUSH -> resolvePush(
                    source.dataDeviceId(), VideoResourceType.CHANNEL, channelId);
            case ChannelDataType.JT_1078 -> resolveJtChannel(
                    source.dataDeviceId(), VideoResourceType.CHANNEL, channelId, false);
            default -> throw inactive(VideoResourceType.CHANNEL, channelId);
        };
    }

    private MediaStreamBinding resolveInvite(
            InviteSessionType inviteType,
            long channelId,
            VideoResourceType requestedType,
            long requestedId) {
        if (channelId > Integer.MAX_VALUE) {
            throw inactive(requestedType, requestedId);
        }
        InviteInfo invite = inviteStreamService.getInviteInfoByDeviceAndChannel(
                inviteType, (int) channelId);
        if (invite == null) {
            throw inactive(requestedType, requestedId);
        }
        StreamInfo streamInfo = invite.getStreamInfo();
        SSRCInfo ssrcInfo = invite.getSsrcInfo();
        String mediaServerId = streamInfo != null && streamInfo.getMediaServer() != null
                ? streamInfo.getMediaServer().getId()
                : invite.getMediaServerId();
        String app = streamInfo != null && hasText(streamInfo.getApp())
                ? streamInfo.getApp()
                : ssrcInfo == null ? null : ssrcInfo.getApp();
        String stream = streamInfo != null && hasText(streamInfo.getStream())
                ? streamInfo.getStream()
                : hasText(invite.getStream())
                        ? invite.getStream()
                        : ssrcInfo == null ? null : ssrcInfo.getStream();
        return requireActive(
                mediaServerId, app, stream, requestedType, requestedId);
    }

    private MediaStreamBinding resolveProxy(
            long proxyId,
            VideoResourceType requestedType,
            long requestedId) {
        if (proxyId > Integer.MAX_VALUE) {
            throw inactive(requestedType, requestedId);
        }
        StreamProxy proxy = streamProxyMapper.select((int) proxyId);
        if (proxy == null || !Boolean.TRUE.equals(proxy.getPulling())) {
            throw inactive(requestedType, requestedId);
        }
        return requireActive(
                proxy.getMediaServerId(),
                proxy.getApp(),
                proxy.getStream(),
                requestedType,
                requestedId);
    }

    private MediaStreamBinding resolvePush(
            long pushId,
            VideoResourceType requestedType,
            long requestedId) {
        if (pushId > Integer.MAX_VALUE) {
            throw inactive(requestedType, requestedId);
        }
        StreamPush push = streamPushMapper.queryOne((int) pushId);
        if (push == null || !push.isPushing()) {
            throw inactive(requestedType, requestedId);
        }
        return requireActive(
                push.getMediaServerId(),
                push.getApp(),
                push.getStream(),
                requestedType,
                requestedId);
    }

    private MediaStreamBinding resolveJtChannel(
            long jtChannelId,
            VideoResourceType requestedType,
            long requestedId,
            boolean playback) {
        if (jtChannelId > Integer.MAX_VALUE) {
            throw inactive(requestedType, requestedId);
        }
        JTChannel channel = jtChannelMapper.selectChannelById((int) jtChannelId);
        if (channel == null) {
            throw inactive(requestedType, requestedId);
        }
        JTDevice terminal = jtTerminalMapper.getDeviceById(channel.getTerminalDbId());
        if (terminal == null || !hasText(terminal.getPhoneNumber())) {
            throw inactive(requestedType, requestedId);
        }
        String prefix = playback
                ? VideoManagerConstants.INVITE_INFO_1078_PLAYBACK
                : VideoManagerConstants.INVITE_INFO_1078_PLAY;
        Object cached = redisTemplate.opsForValue().get(
                prefix + terminal.getPhoneNumber() + ":" + channel.getChannelId());
        if (!(cached instanceof StreamInfo streamInfo)
                || streamInfo.getMediaServer() == null) {
            throw inactive(requestedType, requestedId);
        }
        return requireActive(
                streamInfo.getMediaServer().getId(),
                streamInfo.getApp(),
                streamInfo.getStream(),
                requestedType,
                requestedId);
    }

    private MediaStreamBinding requireActive(
            String mediaServerId,
            String app,
            String stream,
            VideoResourceType resourceType,
            long resourceId) {
        if (!hasText(mediaServerId) || !hasText(app) || !hasText(stream)) {
            throw inactive(resourceType, resourceId);
        }
        MediaStreamBinding binding = new MediaStreamBinding(
                mediaServerId, app, stream, resourceType, resourceId);
        if (!isActive(binding)) {
            throw inactive(resourceType, resourceId);
        }
        return binding;
    }

    private boolean isActive(MediaStreamBinding binding) {
        MediaInfo mediaInfo = redisCatchStorage.getStreamInfo(
                binding.app(), binding.stream(), binding.mediaServerId());
        return mediaInfo != null
                && !Boolean.FALSE.equals(mediaInfo.getOnline())
                && mediaInfo.getMediaServer() != null
                && binding.mediaServerId().equals(mediaInfo.getMediaServer().getId());
    }

    private VideoResourceNotFoundException inactive(
            VideoResourceType type,
            long resourceId) {
        return new VideoResourceNotFoundException(
                "视频资源没有唯一的活动媒体流: " + type + ":" + resourceId);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
