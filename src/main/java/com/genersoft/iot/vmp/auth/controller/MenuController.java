package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.dto.RouteDto;
import com.genersoft.iot.vmp.auth.service.MenuRouteService;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "菜单路由")
@RestController
@RequestMapping("/api/menu")
public class MenuController {

    private final MenuRouteService menuRouteService;

    public MenuController(MenuRouteService menuRouteService) {
        this.menuRouteService = menuRouteService;
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/routers")
    @Operation(summary = "获取当前用户动态路由", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public List<RouteDto> routers() {
        return menuRouteService.currentRoutes();
    }
}
