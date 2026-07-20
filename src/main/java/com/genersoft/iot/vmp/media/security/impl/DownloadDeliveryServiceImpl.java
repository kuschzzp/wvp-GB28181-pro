package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.security.DownloadAccessProperties;
import com.genersoft.iot.vmp.media.security.DownloadDeliveryService;
import com.genersoft.iot.vmp.media.security.DownloadFileGrant;
import com.genersoft.iot.vmp.media.security.DownloadPathGuard;
import com.genersoft.iot.vmp.media.security.DownloadPayload;
import com.genersoft.iot.vmp.media.security.DownloadStorageType;
import com.genersoft.iot.vmp.media.security.DownloadTicketClaims;
import com.genersoft.iot.vmp.media.security.DownloadTicketException;
import com.genersoft.iot.vmp.media.security.DownloadTicketFailureReason;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ContentDisposition;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DownloadDeliveryServiceImpl implements DownloadDeliveryService {

    private final IMediaServerService mediaServerService;
    private final ObjectProvider<Ijt1078Service> jt1078ServiceProvider;
    private final DownloadPathGuard pathGuard;
    private final DownloadAccessProperties properties;
    private final OkHttpClient httpClient;

    public DownloadDeliveryServiceImpl(
            IMediaServerService mediaServerService,
            ObjectProvider<Ijt1078Service> jt1078ServiceProvider,
            DownloadPathGuard pathGuard,
            DownloadAccessProperties properties) {
        this.mediaServerService = mediaServerService;
        this.jt1078ServiceProvider = jt1078ServiceProvider;
        this.pathGuard = pathGuard;
        this.properties = properties;
        this.httpClient = new OkHttpClient.Builder()
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(2, TimeUnit.HOURS)
                .build();
    }

    @Override
    public void deliver(
            DownloadTicketClaims claims,
            HttpServletResponse response) throws IOException {
        DownloadPayload payload = claims.payload();
        response.setContentType(payload.contentType());
        response.setHeader(
                "Content-Disposition",
                disposition(payload).toString());

        switch (payload.deliveryType()) {
            case FILE -> deliverFile(payload.files().getFirst(), response);
            case ZIP -> deliverZip(payload, response);
            case JT_RECORD -> deliverJtRecord(payload, response);
            case JT_SNAPSHOT -> deliverJtSnapshot(payload, response);
        }
    }

    private void deliverFile(
            DownloadFileGrant file,
            HttpServletResponse response) throws IOException {
        if (file.storageType() == DownloadStorageType.LOCAL
                && applyAccelRedirect(file, response)) {
            return;
        }
        try (InputStream input = open(file);
             ServletOutputStream output = response.getOutputStream()) {
            input.transferTo(output);
            output.flush();
        }
    }

    private void deliverZip(
            DownloadPayload payload,
            HttpServletResponse response) throws IOException {
        Set<String> usedNames = new HashSet<>();
        try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
            int index = 1;
            for (DownloadFileGrant file : payload.files()) {
                String entryName = uniqueName(file.fileName(), usedNames, index++);
                zip.putNextEntry(new ZipEntry(entryName));
                try (InputStream input = open(file)) {
                    input.transferTo(zip);
                }
                zip.closeEntry();
            }
            zip.finish();
        }
    }

    private void deliverJtRecord(
            DownloadPayload payload,
            HttpServletResponse response) throws IOException {
        Ijt1078Service service = requireJtService();
        service.recordDownload(payload.opaqueId(), response.getOutputStream());
    }

    private void deliverJtSnapshot(
            DownloadPayload payload,
            HttpServletResponse response) throws IOException {
        byte[] data = requireJtService().snap(
                payload.phoneNumber(), payload.channelNumber());
        try (ServletOutputStream output = response.getOutputStream()) {
            output.write(data);
            output.flush();
        }
    }

    private InputStream open(DownloadFileGrant file) throws IOException {
        if (file.storageType() == DownloadStorageType.LOCAL) {
            DownloadFileGrant checked = pathGuard.revalidate(file);
            return Files.newInputStream(Path.of(checked.path()));
        }
        return openRemote(file);
    }

    private InputStream openRemote(DownloadFileGrant file) throws IOException {
        MediaServer mediaServer = mediaServerService.getOne(file.mediaServerId());
        if (mediaServer == null) {
            mediaServer = mediaServerService.getOneFromDatabase(file.mediaServerId());
        }
        if (mediaServer == null
                || !mediaServer.isStatus()
                || !"zlm".equalsIgnoreCase(mediaServer.getType())
                || mediaServer.getIp() == null
                || mediaServer.getHttpPort() <= 0) {
            throw unavailable("远程媒体节点不可用", null);
        }
        DownloadFileGrant checked = pathGuard.revalidate(file);
        HttpUrl url = new HttpUrl.Builder()
                .scheme("http")
                .host(mediaServer.getIp())
                .port(mediaServer.getHttpPort())
                .addPathSegments("index/api/downloadFile")
                .addQueryParameter("file_path", checked.path())
                .build();
        Response remoteResponse = httpClient.newCall(
                new Request.Builder().get().url(url).build()).execute();
        if (!remoteResponse.isSuccessful()) {
            remoteResponse.close();
            throw unavailable("远程媒体文件读取失败", null);
        }
        ResponseBody body = remoteResponse.body();
        if (body == null) {
            remoteResponse.close();
            throw unavailable("远程媒体文件为空", null);
        }
        InputStream input = body.byteStream();
        return new java.io.FilterInputStream(input) {
            @Override
            public void close() throws IOException {
                try {
                    super.close();
                } finally {
                    remoteResponse.close();
                }
            }
        };
    }

    private boolean applyAccelRedirect(
            DownloadFileGrant file,
            HttpServletResponse response) {
        if (properties.accelRoot().isBlank()) {
            return false;
        }
        try {
            DownloadFileGrant checked = pathGuard.revalidate(file);
            Path accelRoot = Path.of(properties.accelRoot()).toRealPath();
            Path target = Path.of(checked.path()).toRealPath();
            if (!target.startsWith(accelRoot)) {
                return false;
            }
            Path relative = accelRoot.relativize(target);
            StringBuilder internalPath = new StringBuilder(
                    properties.accelInternalPrefix());
            for (Path segment : relative) {
                if (internalPath.charAt(internalPath.length() - 1) != '/') {
                    internalPath.append('/');
                }
                internalPath.append(UriUtils.encodePathSegment(
                        segment.toString(), StandardCharsets.UTF_8));
            }
            response.setHeader("X-Accel-Redirect", internalPath.toString());
            response.setContentLengthLong(Files.size(target));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private ContentDisposition disposition(DownloadPayload payload) {
        ContentDisposition.Builder builder = payload.inline()
                ? ContentDisposition.inline()
                : ContentDisposition.attachment();
        return builder.filename(payload.fileName(), StandardCharsets.UTF_8).build();
    }

    private String uniqueName(String name, Set<String> usedNames, int index) {
        String safe = pathGuard.safeFileName(name, "record-" + index + ".bin");
        if (usedNames.add(safe)) {
            return safe;
        }
        int dot = safe.lastIndexOf('.');
        String stem = dot > 0 ? safe.substring(0, dot) : safe;
        String extension = dot > 0 ? safe.substring(dot) : "";
        String candidate;
        int suffix = 2;
        do {
            candidate = stem + "-" + suffix++ + extension;
        } while (!usedNames.add(candidate));
        return candidate;
    }

    private Ijt1078Service requireJtService() {
        Ijt1078Service service = jt1078ServiceProvider.getIfAvailable();
        if (service == null) {
            throw unavailable("JT1078服务未启用", null);
        }
        return service;
    }

    private DownloadTicketException unavailable(
            String message,
            Throwable cause) {
        return new DownloadTicketException(
                DownloadTicketFailureReason.TARGET_UNAVAILABLE,
                message,
                cause);
    }
}
