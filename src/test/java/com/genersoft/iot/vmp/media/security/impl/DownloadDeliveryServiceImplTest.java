package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.auth.model.PrincipalType;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.genersoft.iot.vmp.media.security.DownloadAccessProperties;
import com.genersoft.iot.vmp.media.security.DownloadDeliveryType;
import com.genersoft.iot.vmp.media.security.DownloadFileGrant;
import com.genersoft.iot.vmp.media.security.DownloadPathGuard;
import com.genersoft.iot.vmp.media.security.DownloadPayload;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.DownloadTicketClaims;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class DownloadDeliveryServiceImplTest {

    @TempDir
    Path tempDir;

    @Test
    void accelRedirectRevalidatesTicketRootAfterFileIsSwapped() throws Exception {
        Path accelRoot = Files.createDirectories(tempDir.resolve("record"));
        Path allowedRoot = Files.createDirectories(accelRoot.resolve("channel-1"));
        Path original = Files.writeString(allowedRoot.resolve("record.mp4"), "record");
        Path outsideTicketRoot = Files.writeString(
                accelRoot.resolve("another-channel.mp4"), "other");
        DownloadPathGuard pathGuard = new DownloadPathGuard();
        DownloadFileGrant file = pathGuard.resolveLocal(
                allowedRoot.toString(), original.toString(), "record.mp4");

        Files.delete(original);
        Files.createSymbolicLink(original, outsideTicketRoot);

        DownloadDeliveryServiceImpl service = new DownloadDeliveryServiceImpl(
                mock(IMediaServerService.class),
                jtServiceProvider(),
                pathGuard,
                new DownloadAccessProperties(
                        Duration.ofMinutes(2),
                        100,
                        "snap",
                        accelRoot.toString(),
                        "/_protected_media/"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThrows(
                IllegalArgumentException.class,
                () -> service.deliver(claims(file), response));
        assertNull(response.getHeader("X-Accel-Redirect"));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<Ijt1078Service> jtServiceProvider() {
        return mock(ObjectProvider.class);
    }

    private DownloadTicketClaims claims(DownloadFileGrant file) {
        Instant issuedAt = Instant.parse("2026-07-15T00:00:00Z");
        return new DownloadTicketClaims(
                PrincipalType.USER,
                1L,
                1L,
                1L,
                DownloadResourceType.CLOUD_RECORD,
                List.of(41L),
                "video:cloud-record:download",
                List.of(new VideoResourceRef(VideoResourceType.CHANNEL, 7L)),
                new DownloadPayload(
                        DownloadDeliveryType.FILE,
                        List.of(file),
                        null,
                        null,
                        null,
                        "video/mp4",
                        "record.mp4",
                        false),
                issuedAt,
                issuedAt.plusSeconds(120));
    }
}
