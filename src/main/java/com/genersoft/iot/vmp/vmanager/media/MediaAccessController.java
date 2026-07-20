package com.genersoft.iot.vmp.vmanager.media;

import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.media.security.PlayTicketService;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@Tag(name = "媒体安全访问")
@RestController
@RequestMapping("/api/media")
@PreAuthorize("isAuthenticated()")
public class MediaAccessController {

    private final PlayTicketService playTicketService;

    public MediaAccessController(PlayTicketService playTicketService) {
        this.playTicketService = Objects.requireNonNull(
                playTicketService, "playTicketService must not be null");
    }

    @PostMapping("/play-ticket")
    @Operation(
            summary = "为已授权资源签发短期播放票据",
            security = @SecurityRequirement(name = JwtUtils.HEADER))
    public PlayTicketResponse issuePlayTicket(
            @Valid @RequestBody PlayTicketRequest request) {
        return playTicketService.issue(request);
    }
}
