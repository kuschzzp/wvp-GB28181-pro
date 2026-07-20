package com.genersoft.iot.vmp.media.security;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.conf.security.JwtAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.dto.PlayTicketResponse;
import com.genersoft.iot.vmp.vmanager.media.MediaAccessController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MediaAccessController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = {JwtAuthenticationFilter.class, ApiKeyAuthenticationFilter.class}))
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class PlayTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayTicketService playTicketService;

    @Test
    void endpointRequiresAnAuthenticatedPrincipal() {
        PreAuthorize annotation = MediaAccessController.class.getAnnotation(PreAuthorize.class);
        assertNotNull(annotation);
        assertEquals("isAuthenticated()", annotation.value());
    }

    @Test
    void clientCanOnlySelectStableResourceAndSchema() throws Exception {
        Instant issuedAt = Instant.parse("2026-07-15T06:00:00Z");
        MediaStreamBinding binding = new MediaStreamBinding(
                "zlm-1", "rtp", "stream-1", VideoResourceType.CHANNEL, 42L);
        when(playTicketService.issue(argThat(request ->
                request.resourceType() == VideoResourceType.CHANNEL
                        && request.resourceId() == 42L
                        && "FLV".equals(request.schema()))))
                .thenReturn(new PlayTicketResponse(
                        "opaque-ticket",
                        issuedAt,
                        issuedAt.plusSeconds(120),
                        binding,
                        Set.of(MediaSchema.FLV)));

        mockMvc.perform(post("/api/media/play-ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceType": "CHANNEL",
                                  "resourceId": 42,
                                  "schema": "FLV",
                                  "mediaServerId": "attacker-node",
                                  "app": "attacker-app",
                                  "stream": "attacker-stream"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").value("opaque-ticket"))
                .andExpect(jsonPath("$.data.binding.mediaServerId").value("zlm-1"))
                .andExpect(jsonPath("$.data.binding.app").value("rtp"))
                .andExpect(jsonPath("$.data.binding.stream").value("stream-1"));

        verify(playTicketService).issue(argThat(request ->
                request.resourceType() == VideoResourceType.CHANNEL
                        && request.resourceId() == 42L
                        && "FLV".equals(request.schema())));
    }

    @Test
    void invalidRequestIsRejectedBeforeTicketService() throws Exception {
        mockMvc.perform(post("/api/media/play-ticket")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "resourceType": "CHANNEL",
                                  "resourceId": 0,
                                  "schema": ""
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(playTicketService);
    }
}
