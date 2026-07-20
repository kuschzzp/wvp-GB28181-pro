package com.genersoft.iot.vmp.vmanager.bean;

import com.genersoft.iot.vmp.common.StreamInfo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.genersoft.iot.vmp.media.security.dto.LegacyMediaResponse;
import com.genersoft.iot.vmp.media.security.dto.PlayableStreamRef;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "流信息")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StreamContent {

    @Schema(description = "无长期 URL 的可播放资源引用")
    private PlayableStreamRef playable;

    @Schema(description = "仅在 audit 兼容期临时返回的已弃用长期地址")
    private LegacyMediaResponse legacyResponse;

    @Schema(description = "应用名")
    private String app;

    @Schema(description = "流ID")
    private String stream;

    @Schema(description = "流媒体ID")
    private String mediaServerId;

    @Schema(description = "开始时间")
    private String startTime;

    @Schema(description = "结束时间")
    private String endTime;

    @Schema(description = "时长(回放时使用)")
    private Double duration;

    private double progress;

    @Schema(description = "录像完成后签发的一次性下载票据")
    private DownloadTicketResponse downloadTicket;

    public StreamContent(StreamInfo streamInfo) {
        this(streamInfo, null);
    }

    public StreamContent(StreamInfo streamInfo, PlayableStreamRef playable) {
        if (streamInfo == null) {
            return;
        }
        this.playable = playable;
        this.app = streamInfo.getApp();
        this.stream = streamInfo.getStream();
        if (streamInfo.getMediaServer() != null) {
            this.mediaServerId = streamInfo.getMediaServer().getId();
        }
        this.startTime = streamInfo.getStartTime();
        this.endTime = streamInfo.getEndTime();
        this.progress = streamInfo.getProgress();
        this.duration = streamInfo.getDuration();
    }

}
