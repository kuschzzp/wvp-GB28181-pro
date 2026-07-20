package com.genersoft.iot.vmp.auth.dto;

import java.util.List;

public record RouteDto(
        String name,
        String path,
        String component,
        RouteMetaDto meta,
        List<RouteDto> children) {
}
