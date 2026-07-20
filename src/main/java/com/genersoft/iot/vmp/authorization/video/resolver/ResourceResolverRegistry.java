package com.genersoft.iot.vmp.authorization.video.resolver;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.util.Set;

/**
 * 视频资源解析端口。外部编号和派生对象必须先解析成内部稳定主键，
 * 再交给授权服务计算范围。
 */
public interface ResourceResolverRegistry {

    VideoResourceRef requireExisting(VideoResourceRef resource);

    default VideoResourceRef requireExisting(VideoResourceType type, long id) {
        return requireExisting(new VideoResourceRef(type, id));
    }

    VideoResourceRef resolveToAuthorizable(VideoResourceRef resource);

    Set<VideoResourceRef> resolveAuthorizationCandidates(VideoResourceRef resource);

    VideoResourceRef resolveGbChannel(String deviceId, String channelId);

    VideoResourceRef resolveGbDevice(String deviceId);

    VideoResourceRef resolveUnifiedChannel(
            String channelDeviceId, String gbDeviceId);

    VideoResourceRef resolveJtTerminal(String phoneNumber);

    VideoResourceRef resolveJtChannel(String phoneNumber, int channelId);

    VideoResourceRef resolveStreamProxy(String app, String stream);

    VideoResourceRef resolveStreamPush(String app, String stream);

    VideoResourceRef resolveAppStream(String app, String stream);

    VideoResourceRef resolveRecord(long recordId);

    VideoResourceRef resolveAlarm(long alarmId);
}
