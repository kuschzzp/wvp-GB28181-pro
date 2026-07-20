package com.genersoft.iot.vmp.authorization.video.resolver;

public record ChannelAuthorizationSource(
        int dataType,
        long dataDeviceId,
        Long jtTerminalId) {
}
