package com.genersoft.iot.vmp.authorization.video.resolver;

public record CloudRecordSource(
        long recordId,
        String sourceType,
        Long sourceId,
        Long channelId) {
}
