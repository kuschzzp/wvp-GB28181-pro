package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.conf.GlobalExceptionHandler;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import com.genersoft.iot.vmp.vmanager.media.SecuredDownloadController;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecuredDownloadControllerTest {

    private DownloadTicketService ticketService;
    private DownloadDeliveryService deliveryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ticketService = mock(DownloadTicketService.class);
        deliveryService = mock(DownloadDeliveryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new SecuredDownloadController(ticketService, deliveryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void issuesTicketFromResourceIdWithoutAcceptingAPath() throws Exception {
        Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
        when(ticketService.issue(any())).thenReturn(new DownloadTicketResponse(
                "A".repeat(43),
                issuedAt,
                issuedAt.plusSeconds(120),
                "/api/media/download/" + "A".repeat(43),
                "record.mp4",
                "video/mp4"));

        mockMvc.perform(post("/api/media/download-ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"resourceType":"CLOUD_RECORD","resourceId":41}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.downloadUrl").value(
                        "/api/media/download/" + "A".repeat(43)))
                .andExpect(jsonPath("$.path").doesNotExist())
                .andExpect(jsonPath("$.url").doesNotExist());
    }

    @Test
    void consumesTicketThenDelegatesOnlyServerSideClaims() throws Exception {
        String ticket = "B".repeat(43);
        DownloadTicketClaims claims = claims();
        when(ticketService.consume(ticket)).thenReturn(claims);

        mockMvc.perform(get("/api/media/download/{ticket}", ticket)
                        .queryParam("file_path", "/etc/passwd")
                        .queryParam("recordId", "999"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));

        verify(ticketService).consume(ticket);
        verify(deliveryService).deliver(eq(claims), any());
    }

    @Test
    void invalidOrAlreadyUsedTicketReturnsNotFound() throws Exception {
        String ticket = "C".repeat(43);
        when(ticketService.consume(ticket)).thenThrow(new DownloadTicketException(
                DownloadTicketFailureReason.NOT_FOUND, "NOT_FOUND"));

        mockMvc.perform(get("/api/media/download/{ticket}", ticket))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.msg").value("NOT_FOUND"));
    }

    private DownloadTicketClaims claims() {
        Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
        VideoResourceRef resource = new VideoResourceRef(
                VideoResourceType.JT_CHANNEL, 7L);
        DownloadPayload payload = new DownloadPayload(
                DownloadDeliveryType.JT_SNAPSHOT,
                List.of(),
                null,
                "13800138000",
                1,
                "image/jpeg",
                "snapshot.jpg",
                true);
        return new DownloadTicketClaims(
                PrincipalType.USER,
                1L,
                1L,
                2L,
                DownloadResourceType.JT_SNAPSHOT,
                List.of(7L),
                "video:jt:record",
                List.of(resource),
                payload,
                issuedAt,
                issuedAt.plusSeconds(120));
    }
}
