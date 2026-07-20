package com.genersoft.iot.vmp.vmanager.media;

import com.genersoft.iot.vmp.auth.annotation.PublicEndpoint;
import com.genersoft.iot.vmp.media.security.DownloadDeliveryService;
import com.genersoft.iot.vmp.media.security.DownloadTicketClaims;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
public class SecuredDownloadController {

    private final DownloadTicketService downloadTicketService;
    private final DownloadDeliveryService downloadDeliveryService;

    public SecuredDownloadController(
            DownloadTicketService downloadTicketService,
            DownloadDeliveryService downloadDeliveryService) {
        this.downloadTicketService = downloadTicketService;
        this.downloadDeliveryService = downloadDeliveryService;
    }

    @PostMapping("/api/media/download-ticket")
    @PreAuthorize("isAuthenticated()")
    public DownloadTicketResponse issue(
            @RequestBody DownloadTicketRequest request) {
        return downloadTicketService.issue(request);
    }

    @GetMapping("/api/media/download/{ticket}")
    @PublicEndpoint(reason = "仅凭短期、一次性、资源绑定下载票据读取服务端已解析文件")
    public void download(
            @PathVariable String ticket,
            HttpServletResponse response) throws IOException {
        response.setHeader("Cache-Control", "no-store, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        DownloadTicketClaims claims = downloadTicketService.consume(ticket);
        downloadDeliveryService.deliver(claims, response);
    }
}
