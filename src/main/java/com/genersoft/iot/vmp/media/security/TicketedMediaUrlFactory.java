package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.common.StreamURL;
import com.genersoft.iot.vmp.media.bean.MediaInfo;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class TicketedMediaUrlFactory {

    private final IMediaServerService mediaServerService;

    public TicketedMediaUrlFactory(IMediaServerService mediaServerService) {
        this.mediaServerService = Objects.requireNonNull(
                mediaServerService, "mediaServerService must not be null");
    }

    public Map<MediaSchema, URI> create(
            MediaStreamBinding binding,
            String rawTicket,
            Set<MediaSchema> schemas) {
        Objects.requireNonNull(binding, "binding must not be null");
        if (rawTicket == null || rawTicket.isBlank()) {
            throw new IllegalArgumentException("rawTicket must not be blank");
        }
        if (schemas == null || schemas.isEmpty()) {
            throw new IllegalArgumentException("schemas must not be empty");
        }
        MediaServer mediaServer = mediaServerService.getOne(binding.mediaServerId());
        if (mediaServer == null || !mediaServer.isStatus()) {
            throw new IllegalStateException(
                    "媒体节点不存在或未在线: " + binding.mediaServerId());
        }
        MediaInfo mediaInfo = mediaServerService.getMediaInfo(
                mediaServer, binding.app(), binding.stream());
        if (mediaInfo == null || Boolean.FALSE.equals(mediaInfo.getOnline())) {
            throw new IllegalStateException(
                    "活动媒体流不存在: " + binding.app() + "/" + binding.stream());
        }
        StreamInfo streamInfo = mediaServerService.getStreamInfoByAppAndStream(
                mediaServer,
                binding.app(),
                binding.stream(),
                mediaInfo,
                null,
                null,
                true);
        if (streamInfo == null) {
            throw new IllegalStateException("媒体节点无法构造播放地址");
        }

        EnumMap<MediaSchema, URI> result = new EnumMap<>(MediaSchema.class);
        for (MediaSchema schema : Set.copyOf(schemas)) {
            String baseUrl = selectBaseUrl(streamInfo, schema);
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("媒体节点不支持播放协议: " + schema);
            }
            result.put(schema, appendTicket(URI.create(baseUrl), rawTicket));
        }
        return Map.copyOf(result);
    }

    private String selectBaseUrl(StreamInfo streamInfo, MediaSchema schema) {
        return switch (schema) {
            case FLV -> first(streamInfo.getHttps_flv(), streamInfo.getFlv());
            case WS_FLV -> first(streamInfo.getWss_flv(), streamInfo.getWs_flv());
            case HLS -> first(streamInfo.getHttps_hls(), streamInfo.getHls());
            case WEBRTC -> first(streamInfo.getRtcs(), streamInfo.getRtc());
            case RTSP -> first(streamInfo.getRtsps(), streamInfo.getRtsp());
            case RTMP -> first(streamInfo.getRtmps(), streamInfo.getRtmp());
        };
    }

    private String first(StreamURL preferred, StreamURL fallback) {
        String preferredUrl = preferred == null ? null : preferred.getUrl();
        return preferredUrl != null ? preferredUrl : fallback == null ? null : fallback.getUrl();
    }

    private URI appendTicket(URI baseUri, String rawTicket) {
        if (baseUri.getScheme() == null || baseUri.getHost() == null) {
            throw new IllegalArgumentException("媒体基础地址不是绝对 URI");
        }
        String encodedTicket = UriUtils.encode(rawTicket, StandardCharsets.UTF_8);
        return UriComponentsBuilder.fromUri(baseUri)
                .replaceQueryParam("ticket")
                .queryParam("ticket", encodedTicket)
                .build(true)
                .toUri();
    }
}
