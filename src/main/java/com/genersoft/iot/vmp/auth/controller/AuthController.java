package com.genersoft.iot.vmp.auth.controller;

import com.genersoft.iot.vmp.auth.annotation.PublicEndpoint;
import com.genersoft.iot.vmp.auth.dto.LoginRequest;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;
import com.genersoft.iot.vmp.auth.dto.ProfileResponse;
import com.genersoft.iot.vmp.auth.service.ProfileService;
import com.genersoft.iot.vmp.auth.service.UserAuthenticationService;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserAuthenticationService authenticationService;
    private final ProfileService profileService;

    public AuthController(
            UserAuthenticationService authenticationService,
            ProfileService profileService) {
        this.authenticationService = authenticationService;
        this.profileService = profileService;
    }

    @PublicEndpoint(reason = "用户名密码登录必须允许匿名请求")
    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public ResponseEntity<WVPResult<LoginResponse>> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        LoginResponse response = authenticationService.login(loginRequest, request.getRemoteAddr());
        return ResponseEntity.ok()
                .header(JwtUtils.HEADER, response.accessToken())
                .body(WVPResult.success(response));
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/profile")
    @Operation(summary = "获取当前用户与权限")
    public ProfileResponse profile() {
        return profileService.currentProfile();
    }
}
