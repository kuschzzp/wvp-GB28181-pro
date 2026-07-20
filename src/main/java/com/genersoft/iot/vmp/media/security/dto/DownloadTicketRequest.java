package com.genersoft.iot.vmp.media.security.dto;

import com.genersoft.iot.vmp.media.security.DownloadResourceType;

import java.util.List;

public record DownloadTicketRequest(
        DownloadResourceType resourceType,
        Long resourceId,
        List<Long> resourceIds) {

    public DownloadTicketRequest {
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
    }

    public static DownloadTicketRequest single(
            DownloadResourceType resourceType,
            long resourceId) {
        return new DownloadTicketRequest(resourceType, resourceId, List.of());
    }

    public static DownloadTicketRequest batch(
            DownloadResourceType resourceType,
            List<Long> resourceIds) {
        return new DownloadTicketRequest(resourceType, null, resourceIds);
    }
}
