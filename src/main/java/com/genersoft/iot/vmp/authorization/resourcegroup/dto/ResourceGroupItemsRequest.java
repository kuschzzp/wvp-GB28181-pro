package com.genersoft.iot.vmp.authorization.resourcegroup.dto;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record ResourceGroupItemsRequest(@NotNull Set<VideoResourceRef> items) {

    public ResourceGroupItemsRequest {
        items = items == null ? Set.of() : Set.copyOf(items);
    }
}
