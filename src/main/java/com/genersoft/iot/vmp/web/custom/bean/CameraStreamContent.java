package com.genersoft.iot.vmp.web.custom.bean;

import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.media.security.dto.PlayableStreamRef;
import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CameraStreamContent extends StreamContent {

    public CameraStreamContent(StreamInfo streamInfo) {
        super(streamInfo);
    }

    public CameraStreamContent(
            StreamInfo streamInfo,
            PlayableStreamRef playableStreamRef) {
        super(streamInfo, playableStreamRef);
    }

    private String name;

    // 0不可动，1可动
    private Integer controlType;


}
