package com.genersoft.iot.vmp.vmanager.media;

import com.genersoft.iot.vmp.auth.annotation.PublicEndpoint;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.media.security.ShareService;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import com.genersoft.iot.vmp.media.security.dto.StreamShareCreateRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamSharePlayRequest;
import com.genersoft.iot.vmp.media.security.dto.StreamShareResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Tag(name = "媒体流分享")
@RestController
public class StreamShareController {

    private final ShareService shareService;

    public StreamShareController(ShareService shareService) {
        this.shareService = Objects.requireNonNull(
                shareService, "shareService must not be null");
    }

    @PostMapping("/api/media/share")
    @PreAuthorize("@authz.check('video:stream:share')")
    @Operation(
            summary = "创建可撤销的限时流分享",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public StreamShareResponse createShare(
            @Valid @RequestBody StreamShareCreateRequest request) {
        return shareService.create(request);
    }

    @DeleteMapping("/api/media/share/{shareId}")
    @PreAuthorize("@authz.check('video:stream:revoke-share')")
    @Operation(
            summary = "撤销流分享",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void revokeShare(@PathVariable String shareId) {
        shareService.revoke(shareId);
    }

    @PostMapping("/api/public/share/{shareId}/play-ticket")
    @PublicEndpoint(reason = "使用不可猜测 shareId、提取码和原子次数限制兑换短期播放票据")
    @Operation(summary = "公开分享兑换短期播放票据")
    public PlayTicketResponse exchangeShare(
            @PathVariable String shareId,
            @Valid @RequestBody StreamSharePlayRequest request) {
        return shareService.exchange(shareId, request);
    }
}
