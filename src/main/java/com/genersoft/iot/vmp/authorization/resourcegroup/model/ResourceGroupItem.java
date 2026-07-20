package com.genersoft.iot.vmp.authorization.resourcegroup.model;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.time.LocalDateTime;

public record ResourceGroupItem(
        Long id,
        long groupId,
        VideoResourceType resourceType,
        long resourceId,
        Long createBy,
        LocalDateTime createTime) {
}
