package com.genersoft.iot.vmp.media.security.impl;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.resolver.ResourceResolverRegistry;
import com.genersoft.iot.vmp.authorization.video.resolver.VideoResourceNotFoundException;
import com.genersoft.iot.vmp.gb28181.bean.Device;
import com.genersoft.iot.vmp.gb28181.bean.DeviceChannel;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.gb28181.service.IDeviceService;
import com.genersoft.iot.vmp.jt1078.bean.JTChannel;
import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import com.genersoft.iot.vmp.jt1078.dao.JTChannelMapper;
import com.genersoft.iot.vmp.jt1078.dao.JTTerminalMapper;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.security.DownloadAccessProperties;
import com.genersoft.iot.vmp.media.security.DownloadDeliveryType;
import com.genersoft.iot.vmp.media.security.DownloadFileGrant;
import com.genersoft.iot.vmp.media.security.DownloadPathGuard;
import com.genersoft.iot.vmp.media.security.DownloadPayload;
import com.genersoft.iot.vmp.media.security.DownloadResourceResolver;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.DownloadTicketGrant;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.AlarmMapper;
import com.genersoft.iot.vmp.storager.dao.CloudRecordServiceMapper;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class DownloadResourceResolverImpl implements DownloadResourceResolver {

    private static final String CLOUD_DOWNLOAD_PERMISSION =
            "video:cloud-record:download";
    private static final String ALARM_PERMISSION = "video:alarm:list";
    private static final String DEVICE_PERMISSION = "video:device:list";
    private static final String GB_RECORD_PERMISSION = "video:record:download";
    private static final String JT_PERMISSION = "video:jt:record";

    private final ResourceResolverRegistry resourceResolverRegistry;
    private final AuthorizationFacade authorizationFacade;
    private final CloudRecordServiceMapper cloudRecordMapper;
    private final AlarmMapper alarmMapper;
    private final IDeviceChannelService deviceChannelService;
    private final IDeviceService deviceService;
    private final JTChannelMapper jtChannelMapper;
    private final JTTerminalMapper jtTerminalMapper;
    private final IMediaServerService mediaServerService;
    private final DownloadPathGuard pathGuard;
    private final DownloadAccessProperties properties;

    public DownloadResourceResolverImpl(
            ResourceResolverRegistry resourceResolverRegistry,
            AuthorizationFacade authorizationFacade,
            CloudRecordServiceMapper cloudRecordMapper,
            AlarmMapper alarmMapper,
            IDeviceChannelService deviceChannelService,
            IDeviceService deviceService,
            JTChannelMapper jtChannelMapper,
            JTTerminalMapper jtTerminalMapper,
            IMediaServerService mediaServerService,
            DownloadPathGuard pathGuard,
            DownloadAccessProperties properties) {
        this.resourceResolverRegistry = resourceResolverRegistry;
        this.authorizationFacade = authorizationFacade;
        this.cloudRecordMapper = cloudRecordMapper;
        this.alarmMapper = alarmMapper;
        this.deviceChannelService = deviceChannelService;
        this.deviceService = deviceService;
        this.jtChannelMapper = jtChannelMapper;
        this.jtTerminalMapper = jtTerminalMapper;
        this.mediaServerService = mediaServerService;
        this.pathGuard = pathGuard;
        this.properties = properties;
    }

    @Override
    public DownloadTicketGrant resolve(DownloadTicketRequest request) {
        if (request == null || request.resourceType() == null) {
            throw new IllegalArgumentException("下载资源类型不能为空");
        }
        if (!request.resourceType().clientIssuable()) {
            throw new IllegalArgumentException("该下载类型不能由客户端直接签发");
        }
        return switch (request.resourceType()) {
            case CLOUD_RECORD -> cloudRecord(singleId(request));
            case CLOUD_RECORD_ZIP -> cloudRecordZip(batchIds(request));
            case ALARM_SNAPSHOT -> alarmSnapshot(singleId(request));
            case GB_SNAPSHOT -> gbSnapshot(singleId(request));
            case JT_SNAPSHOT -> jtSnapshot(singleId(request));
            case GB_RECORD, JT_RECORD -> throw new IllegalArgumentException(
                    "动态录像票据必须由下载任务签发");
        };
    }

    @Override
    public DownloadTicketGrant resolveGbRecord(
            long recordId,
            VideoResourceRef channelResource) {
        if (channelResource == null
                || channelResource.type() != VideoResourceType.CHANNEL) {
            throw new IllegalArgumentException("国标录像下载必须绑定国标通道");
        }
        VideoResourceRef recordResource = resourceResolverRegistry.resolveRecord(recordId);
        if (!channelResource.equals(recordResource)) {
            throw new VideoResourceNotFoundException("录像不存在或不属于当前通道");
        }
        requireAuthorized(GB_RECORD_PERMISSION, List.of(channelResource));
        CloudRecordItem item = requireRecord(recordId);
        DownloadFileGrant file = resolveRecordFile(item);
        return new DownloadTicketGrant(
                DownloadResourceType.GB_RECORD,
                List.of(recordId),
                GB_RECORD_PERMISSION,
                List.of(channelResource),
                new DownloadPayload(
                        DownloadDeliveryType.FILE,
                        List.of(file),
                        null,
                        null,
                        null,
                        contentType(file.fileName()),
                        file.fileName(),
                        false));
    }

    @Override
    public DownloadTicketGrant resolveJtRecord(
            String downloadId,
            VideoResourceRef channelResource) {
        requireUuid(downloadId);
        if (channelResource == null
                || channelResource.type() != VideoResourceType.JT_CHANNEL) {
            throw new IllegalArgumentException("JT录像下载必须绑定JT通道");
        }
        DownloadPayload payload = new DownloadPayload(
                DownloadDeliveryType.JT_RECORD,
                List.of(),
                downloadId,
                null,
                null,
                "video/mp4",
                pathGuard.safeFileName(downloadId + ".mp4", "jt-record.mp4"),
                false);
        return new DownloadTicketGrant(
                DownloadResourceType.JT_RECORD,
                List.of(channelResource.id()),
                JT_PERMISSION,
                List.of(channelResource),
                payload);
    }

    private DownloadTicketGrant cloudRecord(long id) {
        VideoResourceRef resource = resourceResolverRegistry.resolveRecord(id);
        requireAuthorized(CLOUD_DOWNLOAD_PERMISSION, List.of(resource));
        CloudRecordItem item = requireRecord(id);
        DownloadFileGrant file = resolveRecordFile(item);
        return new DownloadTicketGrant(
                DownloadResourceType.CLOUD_RECORD,
                List.of(id),
                CLOUD_DOWNLOAD_PERMISSION,
                List.of(resource),
                new DownloadPayload(
                        DownloadDeliveryType.FILE,
                        List.of(file),
                        null,
                        null,
                        null,
                        contentType(file.fileName()),
                        file.fileName(),
                        false));
    }

    private DownloadTicketGrant cloudRecordZip(List<Long> ids) {
        LinkedHashSet<VideoResourceRef> resources = new LinkedHashSet<>();
        for (Long id : ids) {
            resources.add(resourceResolverRegistry.resolveRecord(id));
        }
        List<VideoResourceRef> authorizedResources = List.copyOf(resources);
        requireAuthorized(CLOUD_DOWNLOAD_PERMISSION, authorizedResources);
        List<DownloadFileGrant> files = new ArrayList<>(ids.size());
        for (Long id : ids) {
            CloudRecordItem item = requireRecord(id);
            files.add(resolveRecordFile(item));
        }
        return new DownloadTicketGrant(
                DownloadResourceType.CLOUD_RECORD_ZIP,
                ids,
                CLOUD_DOWNLOAD_PERMISSION,
                authorizedResources,
                new DownloadPayload(
                        DownloadDeliveryType.ZIP,
                        files,
                        null,
                        null,
                        null,
                        "application/zip",
                        "cloud-records.zip",
                        false));
    }

    private DownloadTicketGrant alarmSnapshot(long id) {
        VideoResourceRef resource = resourceResolverRegistry.resolveAlarm(id);
        requireAuthorized(ALARM_PERMISSION, List.of(resource));
        String snapPath = alarmMapper.getSnapPathById(id);
        if (snapPath == null || snapPath.isBlank()) {
            throw new VideoResourceNotFoundException("告警快照不存在");
        }
        String root = Path.of(properties.snapshotRoot()).toAbsolutePath().normalize().toString();
        String candidate = Path.of(snapPath).toAbsolutePath().normalize().toString();
        DownloadFileGrant file = pathGuard.resolveLocal(
                root, candidate, "alarm-" + id + ".jpg");
        return fileGrant(
                DownloadResourceType.ALARM_SNAPSHOT,
                id,
                ALARM_PERMISSION,
                resource,
                file,
                true);
    }

    private DownloadTicketGrant gbSnapshot(long id) {
        VideoResourceRef resource = new VideoResourceRef(
                VideoResourceType.CHANNEL, id);
        requireAuthorized(DEVICE_PERMISSION, List.of(resource));
        DeviceChannel channel = deviceChannelService.getOneById(Math.toIntExact(id));
        if (channel == null) {
            throw new VideoResourceNotFoundException("通道快照不存在");
        }
        Device device = deviceService.getDevice(channel.getDataDeviceId());
        if (device == null || device.getDeviceId() == null || channel.getDeviceId() == null) {
            throw new VideoResourceNotFoundException("通道快照来源不存在");
        }
        String fileName = safeSnapshotPart(device.getDeviceId()) + "_"
                + safeSnapshotPart(channel.getDeviceId()) + ".jpg";
        Path root = Path.of(properties.snapshotRoot()).toAbsolutePath().normalize();
        DownloadFileGrant file = pathGuard.resolveLocal(
                root.toString(), root.resolve(fileName).toString(), fileName);
        return fileGrant(
                DownloadResourceType.GB_SNAPSHOT,
                id,
                DEVICE_PERMISSION,
                resource,
                file,
                true);
    }

    private DownloadTicketGrant jtSnapshot(long id) {
        VideoResourceRef resource = new VideoResourceRef(
                VideoResourceType.JT_CHANNEL, id);
        requireAuthorized(JT_PERMISSION, List.of(resource));
        JTChannel channel = jtChannelMapper.selectChannelById(Math.toIntExact(id));
        if (channel == null || channel.getChannelId() == null || channel.getChannelId() <= 0) {
            throw new VideoResourceNotFoundException("JT通道不存在");
        }
        JTDevice terminal = jtTerminalMapper.getDeviceById(channel.getTerminalDbId());
        if (terminal == null || terminal.getPhoneNumber() == null) {
            throw new VideoResourceNotFoundException("JT终端不存在");
        }
        DownloadPayload payload = new DownloadPayload(
                DownloadDeliveryType.JT_SNAPSHOT,
                List.of(),
                null,
                terminal.getPhoneNumber(),
                channel.getChannelId(),
                "image/jpeg",
                "jt-snapshot-" + id + ".jpg",
                true);
        return new DownloadTicketGrant(
                DownloadResourceType.JT_SNAPSHOT,
                List.of(id),
                JT_PERMISSION,
                List.of(resource),
                payload);
    }

    private void requireAuthorized(
            String permissionCode,
            List<VideoResourceRef> resources) {
        authorizationFacade.requireAllVideoResources(permissionCode, resources);
    }

    private DownloadTicketGrant fileGrant(
            DownloadResourceType type,
            long id,
            String permission,
            VideoResourceRef resource,
            DownloadFileGrant file,
            boolean inline) {
        return new DownloadTicketGrant(
                type,
                List.of(id),
                permission,
                List.of(resource),
                new DownloadPayload(
                        DownloadDeliveryType.FILE,
                        List.of(file),
                        null,
                        null,
                        null,
                        contentType(file.fileName()),
                        file.fileName(),
                        inline));
    }

    private CloudRecordItem requireRecord(long id) {
        if (id > Integer.MAX_VALUE) {
            throw new VideoResourceNotFoundException("录像不存在");
        }
        CloudRecordItem item = cloudRecordMapper.queryOne((int) id);
        if (item == null) {
            throw new VideoResourceNotFoundException("录像不存在");
        }
        return item;
    }

    private DownloadFileGrant resolveRecordFile(CloudRecordItem item) {
        MediaServer mediaServer = mediaServerService.getOne(item.getMediaServerId());
        if (mediaServer == null) {
            mediaServer = mediaServerService.getOneFromDatabase(item.getMediaServerId());
        }
        if (mediaServer == null
                || mediaServer.getRecordPath() == null
                || mediaServer.getRecordPath().isBlank()) {
            throw new VideoResourceNotFoundException("录像媒体节点不存在");
        }
        String fallback = "record-" + item.getId() + ".mp4";
        String fileName = pathGuard.safeFileName(item.getFileName(), fallback);
        boolean remoteAllowed = mediaServer.isStatus()
                && "zlm".equalsIgnoreCase(mediaServer.getType());
        return pathGuard.resolveMediaFile(
                mediaServer.getRecordPath(),
                item.getFilePath(),
                fileName,
                mediaServer.getId(),
                remoteAllowed);
    }

    private long singleId(DownloadTicketRequest request) {
        if (request.resourceId() == null
                || request.resourceId() <= 0
                || !request.resourceIds().isEmpty()) {
            throw new IllegalArgumentException("单文件下载必须且只能提供 resourceId");
        }
        return request.resourceId();
    }

    private List<Long> batchIds(DownloadTicketRequest request) {
        if (request.resourceId() != null
                || request.resourceIds().isEmpty()
                || request.resourceIds().size() > properties.maxBatchSize()
                || request.resourceIds().stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("批量下载资源 ID 不合法");
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>(request.resourceIds());
        if (unique.size() != request.resourceIds().size()) {
            throw new IllegalArgumentException("批量下载资源 ID 不能重复");
        }
        return List.copyOf(unique);
    }

    private String safeSnapshotPart(String value) {
        if (value == null
                || value.isBlank()
                || value.contains("..")
                || !value.matches("[A-Za-z0-9_-]{1,128}")) {
            throw new IllegalArgumentException("快照标识不合法");
        }
        return value;
    }

    private String contentType(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".mp4")) {
            return "video/mp4";
        }
        return "application/octet-stream";
    }

    private void requireUuid(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("下载 ID 不能为空");
        }
        try {
            UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("下载 ID 不合法", exception);
        }
    }
}
