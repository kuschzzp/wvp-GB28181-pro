package com.genersoft.iot.vmp.auth.menu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record MenuRequest(
        @PositiveOrZero long parentId,
        @NotBlank @Size(max = 64) String menuName,
        @NotBlank @Pattern(regexp = "^[MCF]$") String menuType,
        @Size(max = 64) String routeName,
        @Size(max = 255) String path,
        @Size(max = 255) String component,
        @Size(max = 128) String perms,
        @Size(max = 100) String icon,
        int orderNum,
        @Min(0) @Max(1) int visible,
        @Min(0) @Max(1) int status,
        boolean cacheable,
        boolean external,
        boolean systemMenu) {
}
