package com.genersoft.iot.vmp.web.custom;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.DerivedResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.StreamResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.DynamicTask;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.security.PlayableStreamService;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import com.genersoft.iot.vmp.media.security.dto.PlayableStreamRef;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.media.zlm.dto.StreamAuthorityInfo;
import com.genersoft.iot.vmp.service.ICloudRecordService;
import com.genersoft.iot.vmp.service.bean.ErrorCallback;
import com.genersoft.iot.vmp.service.bean.InviteErrorCode;
import com.genersoft.iot.vmp.storager.IRedisCatchStorage;
import com.genersoft.iot.vmp.streamPush.bean.StreamPush;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushPlayService;
import com.genersoft.iot.vmp.streamPush.service.IStreamPushService;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import com.genersoft.iot.vmp.web.custom.bean.*;
import com.genersoft.iot.vmp.web.custom.service.CameraChannelService;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.UUID;

@Tag(name  = "第三方接口")
@Slf4j
@RestController
@RequestMapping(value = "/api/sy")
@ConditionalOnProperty(value = "sy.enable", havingValue = "true")
@Hidden
@PreAuthorize("@authz.check('video:channel:list')")
public class CameraChannelController {

    private static final String CHANNEL_LIST_PERMISSION = "video:channel:list";
    private static final String CHANNEL_PLAY_PERMISSION = "video:channel:play";
    private static final String CHANNEL_STOP_PERMISSION = "video:channel:stop";
    private static final String CHANNEL_EDIT_PERMISSION = "video:channel:edit";
    private static final String PTZ_CONTROL_PERMISSION = "video:ptz:control";
    private static final String PUSH_PLAY_PERMISSION = "video:push:play";
    private static final String PUSH_STOP_PERMISSION = "video:push:stop";
    private static final String CLOUD_RECORD_DOWNLOAD_PERMISSION =
            "video:cloud-record:download";
    private static final String CLOUD_RECORD_COLLECT_PERMISSION =
            "video:cloud-record:collect";

    @Autowired
    private CameraChannelService channelService;

    @Autowired
    private UserSetting userSetting;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private ICloudRecordService cloudRecordService;

    @Autowired
    private AuthorizationFacade authorizationFacade;

    @Autowired
    private DerivedResourceAccessAuthorizer derivedResourceAccessAuthorizer;

    @Autowired
    private GbChannelAccessAuthorizer channelAccessAuthorizer;

    @Autowired
    private StreamResourceAccessAuthorizer streamResourceAccessAuthorizer;

    @Autowired
    private IStreamPushPlayService streamPushPlayService;

    @Autowired
    private DynamicTask dynamicTask;

    @Autowired
    private IStreamPushService streamPushService;

    @Autowired
    private PlayableStreamService playableStreamService;

    @Autowired
    private DownloadTicketService downloadTicketService;

    @Value("${sy.ptz-control-time-interval}")
    private int ptzControlTimeInterval = 300;

    @GetMapping(value = "/camera/list")
    @ResponseBody
    @Operation(summary = "查询摄像机列表, 只查询当前虚拟组织下的", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页")
    @Parameter(name = "count", description = "每页查询数量")
    @Parameter(name = "groupAlias", description = "分组别名")
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    @Parameter(name = "status", description = "摄像头状态")
    public PageInfo<CameraChannel> queryList(@RequestParam(required = false, value = "page", defaultValue = "1" )Integer page,
                                        @RequestParam(required = false, value = "count", defaultValue = "100")Integer count,
                                        String groupAlias,
                                        @RequestParam(required = false) String geoCoordSys,
                                        @RequestParam(required = false) Boolean status){


        return channelService.queryList(
                page,
                count,
                groupAlias,
                status,
                geoCoordSys,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/camera/list-with-child")
    @ResponseBody
    @Operation(summary = "查询摄像机列表, 查询当前虚拟组织下以及全部子节点", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页")
    @Parameter(name = "count", description = "每页查询数量")
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "sortName", description = "排序字段名")
    @Parameter(name = "order", description = "排序方式（true: 升序 或 false: 降序 ）")
    @Parameter(name = "groupAlias", description = "分组别名")
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    @Parameter(name = "status", description = "摄像头状态")
    public PageInfo<CameraChannel> queryListWithChild(@RequestParam(required = false, value = "page", defaultValue = "1" )Integer page,

                                        @RequestParam(required = false, value = "count", defaultValue = "100")Integer count,
                                        @RequestParam(required = false) String query,
                                        @RequestParam(required = false) String sortName,
                                        @RequestParam(required = false) Boolean order,
                                        @RequestParam(required = false) String groupAlias,
                                        @RequestParam(required = false) String geoCoordSys,
                                        @RequestParam(required = false) Boolean status){
        if (ObjectUtils.isEmpty(query)) {
            query = null;
        }
        if (ObjectUtils.isEmpty(sortName)) {
            sortName = null;
        }
        if (ObjectUtils.isEmpty(order)) {
            order = null;
        }
        if (ObjectUtils.isEmpty(groupAlias)) {
            groupAlias = null;
        }

        return channelService.queryListWithChild(
                page,
                count,
                query,
                sortName,
                order,
                groupAlias,
                status,
                geoCoordSys,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/camera/cont-with-child")
    @ResponseBody
    @Operation(summary = "查询摄像机列表的总数和在线数", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "groupAlias", description = "分组别名")
    public List<CameraCount> queryCountWithChild(String groupAlias){
        return channelService.queryCountWithChild(
                groupAlias,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/camera/one")
    @ResponseBody
    @Operation(summary = "查询单个摄像头信息", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "deviceId", description = "通道编号")
    @Parameter(name = "deviceCode", description = "摄像头设备国标编号, 对于非国标摄像头可以不设置此参数")
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    public CameraChannel getOne(String deviceId, @RequestParam(required = false) String deviceCode,
                                  @RequestParam(required = false) String geoCoordSys) {
        channelAccessAuthorizer.requireUnifiedChannelHidden(
                CHANNEL_LIST_PERMISSION, deviceId, deviceCode);
        return channelService.queryOne(deviceId, deviceCode, geoCoordSys);
    }

    @PreAuthorize("@authz.check('video:channel:edit')")
    @GetMapping(value = "/camera/update")
    @ResponseBody
    @Operation(summary = "更新摄像头信息", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "deviceId", description = "通道编号")
    @Parameter(name = "deviceCode", description = "摄像头设备国标编号, 对于非国标摄像头可以不设置此参数")
    @Parameter(name = "name", description = "通道名称")
    @Parameter(name = "longitude", description = "经度")
    @Parameter(name = "latitude", description = "纬度")
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    public void updateCamera(String deviceId,
                                      @RequestParam(required = false) String deviceCode,
                                      @RequestParam(required = false) String name,
                                      @RequestParam(required = false) Double longitude,
                                      @RequestParam(required = false) Double latitude,
                                      @RequestParam(required = false) String geoCoordSys) {
        channelAccessAuthorizer.requireUnifiedChannel(
                CHANNEL_EDIT_PERMISSION, deviceId, deviceCode);
        channelService.updateCamera(deviceId, deviceCode, name, longitude, latitude, geoCoordSys);
    }

    @PostMapping(value = "/camera/list/ids")
    @ResponseBody
    @Operation(summary = "根据编号查询多个摄像头信息", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public List<CameraChannel> queryListByDeviceIds(@RequestBody IdsQueryParam param) {
        return channelService.queryListByDeviceIds(
                param.getDeviceIds(),
                param.getGeoCoordSys(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/camera/list/box")
    @ResponseBody
    @Operation(summary = "根据矩形查询摄像头", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "minLongitude", description = "最小经度")
    @Parameter(name = "maxLongitude", description = "最大经度")
    @Parameter(name = "minLatitude", description = "最小纬度")
    @Parameter(name = "maxLatitude", description = "最大纬度")
    @Parameter(name = "level", description = "地图级别")
    @Parameter(name = "groupAlias", description = "分组别名")
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    public List<CameraChannel> queryListInBox(Double minLongitude, Double maxLongitude,
                                              Double minLatitude, Double maxLatitude,
                                              @RequestParam(required = false) Integer level,
                                              String groupAlias,
                                              @RequestParam(required = false) String geoCoordSys) {
        return channelService.queryListInBox(
                minLongitude,
                maxLongitude,
                minLatitude,
                maxLatitude,
                level,
                groupAlias,
                geoCoordSys,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @PostMapping(value = "/camera/list/polygon")
    @ResponseBody
    @Operation(summary = "根据多边形查询摄像头", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public List<CameraChannel> queryListInPolygon(@RequestBody PolygonQueryParam param) {
        return channelService.queryListInPolygon(
                param.getPosition(),
                param.getGroupAlias(),
                param.getLevel(),
                param.getGeoCoordSys(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/camera/list/circle")
    @ResponseBody
    @Operation(summary = "根据圆范围查询摄像头", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "centerLongitude", description = "圆心经度")
    @Parameter(name = "centerLatitude", description = "圆心纬度")
    @Parameter(name = "radius", description = "查询范围的半径，单位米")
    @Parameter(name = "level", description = "地图级别")
    @Parameter(name = "groupAlias", description = "分组别名")
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    public List<CameraChannel> queryListInCircle(Double centerLongitude, Double centerLatitude, Double radius, String groupAlias,
                                                 @RequestParam(required = false) String geoCoordSys, @RequestParam(required = false) Integer level) {
        return channelService.queryListInCircle(
                centerLongitude,
                centerLatitude,
                radius,
                level,
                groupAlias,
                geoCoordSys,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/camera/list/address")
    @ResponseBody
    @Operation(summary = "根据安装地址和监视方位获取摄像头", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "address", description = "安装地址")
    @Parameter(name = "directionType", description = "监视方位", required = false)
    @Parameter(name = "geoCoordSys", description = "坐标系类型：WGS84,GCJ02、BD09")
    public List<CameraChannel> queryListByAddressAndDirectionType(String address, @RequestParam(required = false) Integer directionType, @RequestParam(required = false) String geoCoordSys) {
        return channelService.queryListByAddressAndDirectionType(
                address,
                directionType,
                geoCoordSys,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @PreAuthorize("@authz.check('video:channel:play')")
    @GetMapping(value = "/camera/control/play")
    @ResponseBody
    @Operation(summary = "播放摄像头", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "deviceId", description = "通道编号")
    @Parameter(name = "deviceCode", description = "摄像头设备国标编号, 对于非国标摄像头可以不设置此参数")
    public DeferredResult<WVPResult<CameraStreamContent>> play(HttpServletRequest request, String deviceId, @RequestParam(required = false) String deviceCode) {

        VideoResourceRef resource = channelAccessAuthorizer.requireUnifiedChannel(
                CHANNEL_PLAY_PERMISSION, deviceId, deviceCode);
        log.info("[SY-播放摄像头] API调用，deviceId：{} ，deviceCode：{} ",deviceId, deviceCode);
        DeferredResult<WVPResult<CameraStreamContent>> result = new DeferredResult<>(userSetting.getPlayTimeout().longValue());

        ErrorCallback<CameraStreamInfo> callback = (code, msg, cameraStreamInfo) -> {
            if (code == InviteErrorCode.SUCCESS.getCode()) {
                StreamInfo streamInfo = cameraStreamInfo.getStreamInfo();
                CommonGBChannel channel = cameraStreamInfo.getChannel();
                WVPResult<CameraStreamContent> wvpResult = WVPResult.success();
                if (cameraStreamInfo.getStreamInfo() != null) {
                    if (userSetting.getUseSourceIpAsStreamIp()) {
                        streamInfo=streamInfo.clone();//深拷贝
                        String host;
                        try {
                            URL url=new URL(request.getRequestURL().toString());
                            host=url.getHost();
                        } catch (MalformedURLException e) {
                            host=request.getLocalAddr();
                        }
                        streamInfo.changeStreamIp(host);
                    }
                    if (!ObjectUtils.isEmpty(streamInfo.getMediaServer().getTranscodeSuffix())
                            && !"null".equalsIgnoreCase(streamInfo.getMediaServer().getTranscodeSuffix())) {
                        streamInfo.setStream(streamInfo.getStream() + "_" + streamInfo.getMediaServer().getTranscodeSuffix());
                    }
                    StreamContent safeStream = playableStreamService.publish(
                            streamInfo, resource);
                    CameraStreamContent cameraStreamContent = new CameraStreamContent(
                            streamInfo, safeStream.getPlayable());
                    cameraStreamContent.setName(channel.getGbName());
                    if (channel.getGbPtzType() != null) {
                        cameraStreamContent.setControlType(
                                (channel.getGbPtzType() == 1 || channel.getGbPtzType() == 4 || channel.getGbPtzType() == 5) ? 1 : 0);
                    }else {
                        cameraStreamContent.setControlType(0);
                    }

                    wvpResult.setData(cameraStreamContent);
                }else {
                    wvpResult.setCode(code);
                    wvpResult.setMsg(msg);
                }
                result.setResult(wvpResult);
            }else {
                result.setResult(WVPResult.fail(code, msg));
            }
        };
        channelService.play(deviceId, deviceCode, callback);
        return result;
    }

    @PreAuthorize("@authz.check('video:channel:stop')")
    @GetMapping(value = "/camera/control/stop")
    @ResponseBody
    @Operation(summary = "停止播放摄像头", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "deviceId", description = "通道编号")
    @Parameter(name = "deviceCode", description = "摄像头设备国标编号, 对于非国标摄像头可以不设置此参数")
    public void stopPlay(String deviceId, @RequestParam(required = false) String deviceCode) {
        channelAccessAuthorizer.requireUnifiedChannel(
                CHANNEL_STOP_PERMISSION, deviceId, deviceCode);
        log.info("[SY-停止播放摄像头] API调用，deviceId：{} ，deviceCode：{} ",deviceId, deviceCode);
        channelService.stopPlay(deviceId, deviceCode);
    }

    @Operation(summary = "云台控制", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "deviceId", description = "通道编号")
    @Parameter(name = "deviceCode", description = "摄像头设备国标编号, 对于非国标摄像头可以不设置此参数")
    @Parameter(name = "command", description = "控制指令,允许值: left, right, up, down, upleft, upright, downleft, downright, zoomin, zoomout, stop", required = true)
    @Parameter(name = "speed", description = "速度(0-100)", required = true)
    @PreAuthorize("@authz.check('video:ptz:control')")
    @GetMapping("/camera/control/ptz")
    public DeferredResult<WVPResult<String>> ptz(String deviceId, @RequestParam(required = false) String deviceCode, String command, Integer speed){

        channelAccessAuthorizer.requireUnifiedChannel(
                PTZ_CONTROL_PERMISSION, deviceId, deviceCode);
        log.info("[SY-云台控制] API调用，deviceId：{} ，deviceCode：{} ，command：{} ，speed：{} ",deviceId, deviceCode, command, speed);

        DeferredResult<WVPResult<String>> result = new DeferredResult<>();

        result.onTimeout(()->{
            WVPResult<String> wvpResult = WVPResult.fail(ErrorCode.ERROR100.getCode(), "请求超时");
            result.setResult(wvpResult);
        });

        channelService.ptz(deviceId, deviceCode, command, speed, (code, msg, data) -> {
            WVPResult<String> wvpResult = new WVPResult<>();
            wvpResult.setCode(code);
            wvpResult.setMsg(msg);
            wvpResult.setData(data);
            result.setResult(wvpResult);
        });
        // 设置时间间隔后自动发送停止
        if (!command.equalsIgnoreCase("stop")) {
            dynamicTask.startDelay(UUID.randomUUID().toString(), () -> {
                channelService.ptz(deviceId, deviceCode, "stop", speed, (code, msg, data) -> {});
            }, ptzControlTimeInterval);
        }
        return result;
    }

    @GetMapping(value = "/camera/list-for-mobile")
    @ResponseBody
    @Operation(summary = "查询移动设备摄像机列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页")
    @Parameter(name = "count", description = "每页查询数量")
    @Parameter(name = "topGroupAlias", description = "分组别名")
    public PageInfo<CameraChannel> queryListForMobile(@RequestParam(required = false, value = "page", defaultValue = "1" )Integer page,
                                                      @RequestParam(required = false, value = "count", defaultValue = "100")Integer count,
                                                      @RequestParam(required = false) String topGroupAlias){

        return channelService.queryListForMobile(
                page,
                count,
                topGroupAlias,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }


    @Operation(summary = "获取推流播放地址", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流id", required = true)
    @Parameter(name = "callId", description = "推流时携带的自定义鉴权ID", required = true)
    @PreAuthorize("@authz.check('video:push:play')")
    @GetMapping(value = "/push/play")
    @ResponseBody
    public DeferredResult<WVPResult<StreamContent>> getStreamInfoByAppAndStream(HttpServletRequest request,
                                                                                String app,
                                                                                String stream,
                                                                                String callId){
        VideoResourceRef resource = streamResourceAccessAuthorizer.requirePush(
                PUSH_PLAY_PERMISSION, app, stream);
        StreamPush streamPush = streamPushService.getPush(app, stream);
        Assert.notNull(streamPush, "地址不存在");

        // 权限校验
        StreamAuthorityInfo streamAuthorityInfo = redisCatchStorage.getStreamAuthorityInfo(app, stream);
        if (streamAuthorityInfo == null
                || streamAuthorityInfo.getCallId() == null
                || !streamAuthorityInfo.getCallId().equals(callId)) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "播放地址鉴权失败");
        }

        DeferredResult<WVPResult<StreamContent>> result = new DeferredResult<>(userSetting.getPlayTimeout().longValue());
        result.onTimeout(()->{
            WVPResult<StreamContent> fail = WVPResult.fail(ErrorCode.ERROR100.getCode(), "等待推流超时");
            result.setResult(fail);
        });

        streamPushPlayService.start(streamPush.getId(), (code, msg, streamInfo) -> {
            if (code == 0 && streamInfo != null) {
                streamInfo=streamInfo.clone();//深拷贝
                String host;
                try {
                    URL url=new URL(request.getRequestURL().toString());
                    host=url.getHost();
                } catch (MalformedURLException e) {
                    host=request.getLocalAddr();
                }
                streamInfo.changeStreamIp(host);
                WVPResult<StreamContent> success = WVPResult.success(
                        playableStreamService.publish(streamInfo, resource));
                result.setResult(success);
            }
        }, null, null);
        return result;
    }

    @Operation(summary = "获取推流播放地址（不做检查）", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流id", required = true)
    @Parameter(name = "callId", description = "推流时携带的自定义鉴权ID", required = true)
    @PreAuthorize("@authz.check('video:push:play')")
    @GetMapping(value = "/push/play-without-check")
    @ResponseBody
    public StreamContent getStreamInfoByAppAndStreamWithoutCheck(HttpServletRequest request,
                                                                                String app,
                                                                                String stream,
                                                                                String callId){

        VideoResourceRef resource = streamResourceAccessAuthorizer.requirePush(
                PUSH_PLAY_PERMISSION, app, stream);
        MediaServer mediaServer = mediaServerService.getDefaultMediaServer();
        Assert.notNull(mediaServer, "流媒体服务器不存在");
        StreamInfo streamInfo = mediaServerService.getStreamInfoByAppAndStream(mediaServer, app, stream, null, callId);
        streamInfo=streamInfo.clone();//深拷贝
        String host;
        try {
            URL url=new URL(request.getRequestURL().toString());
            host=url.getHost();
        } catch (MalformedURLException e) {
            host=request.getLocalAddr();
        }
        streamInfo.changeStreamIp(host);
        return playableStreamService.publish(streamInfo, resource);
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:collect')")
    @GetMapping("/record/collect/add")
    @Operation(summary = "添加收藏")
    @Parameter(name = "app", description = "应用名", required = false)
    @Parameter(name = "stream", description = "流ID", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "startTime", description = "鉴权ID", required = false)
    @Parameter(name = "endTime", description = "鉴权ID", required = false)
    @Parameter(name = "callId", description = "鉴权ID", required = false)
    @Parameter(name = "recordId", description = "录像记录的ID，用于精准收藏一个视频文件", required = false)
    public int addCollect(@RequestParam(required = false) String app, @RequestParam(required = false) String stream, @RequestParam(required = false) String mediaServerId, @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime, @RequestParam(required = false) String callId, @RequestParam(required = false) Integer recordId) {
        log.info("[云端录像] 添加收藏，app={}，stream={},mediaServerId={},startTime={},endTime={},callId={},recordId={}", app, stream, mediaServerId, startTime, endTime, callId, recordId);
        if (recordId != null) {
            derivedResourceAccessAuthorizer.requireRecord(
                    CLOUD_RECORD_COLLECT_PERMISSION, recordId);
            return cloudRecordService.changeCollectById(recordId, true);
        } else {
            return cloudRecordService.changeCollect(
                    true, app, stream, mediaServerId, startTime, endTime, callId,
                    authorizationFacade.resolveVideoScopeQuery(
                            CLOUD_RECORD_COLLECT_PERMISSION));
        }
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:collect')")
    @GetMapping("/record/collect/delete")
    @Operation(summary = "移除收藏")
    @Parameter(name = "app", description = "应用名", required = false)
    @Parameter(name = "stream", description = "流ID", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "startTime", description = "鉴权ID", required = false)
    @Parameter(name = "endTime", description = "鉴权ID", required = false)
    @Parameter(name = "callId", description = "鉴权ID", required = false)
    @Parameter(name = "recordId", description = "录像记录的ID，用于精准精准移除一个视频文件的收藏", required = false)
    public int deleteCollect(@RequestParam(required = false) String app, @RequestParam(required = false) String stream, @RequestParam(required = false) String mediaServerId, @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime, @RequestParam(required = false) String callId, @RequestParam(required = false) Integer recordId) {
        log.info("[云端录像] 移除收藏，app={}，stream={},mediaServerId={},startTime={},endTime={},callId={},recordId={}", app, stream, mediaServerId, startTime, endTime, callId, recordId);
        if (recordId != null) {
            derivedResourceAccessAuthorizer.requireRecord(
                    CLOUD_RECORD_COLLECT_PERMISSION, recordId);
            return cloudRecordService.changeCollectById(recordId, false);
        } else {
            return cloudRecordService.changeCollect(
                    false, app, stream, mediaServerId, startTime, endTime, callId,
                    authorizationFacade.resolveVideoScopeQuery(
                            CLOUD_RECORD_COLLECT_PERMISSION));
        }
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:download')")
    @GetMapping("/record/zip")
    public DownloadTicketResponse downloadZipFile(
            @RequestParam List<Integer> ids) {
        derivedResourceAccessAuthorizer.requireAllRecords(
                CLOUD_RECORD_DOWNLOAD_PERMISSION, ids);
        return downloadTicketService.issue(DownloadTicketRequest.batch(
                DownloadResourceType.CLOUD_RECORD_ZIP,
                ids.stream().map(Integer::longValue).toList()));
    }

    @PreAuthorize("@authz.check('video:push:stop')")
    @GetMapping(value = "/forceClose")
    @ResponseBody
    @Operation(summary = "强制停止推流", security = @SecurityRequirement(name = JwtUtils.HEADER))
    public void stop(String app, String stream){
        streamResourceAccessAuthorizer.requirePush(
                PUSH_STOP_PERMISSION, app, stream);
        streamPushPlayService.stop(app, stream);
    }

    @GetMapping(value = "/camera/meeting/list")
    @ResponseBody
    @Operation(summary = "查询会议设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "topGroupAlias", description = "分组别名")
    public List<CameraChannel> queryMeetingChannelList(String topGroupAlias){
        return channelService.queryMeetingChannelList(
                topGroupAlias,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @GetMapping(value = "/test")
    @ResponseBody
    public SYMember test(String device){
        channelAccessAuthorizer.requireUnifiedChannelHidden(
                CHANNEL_LIST_PERMISSION, device, null);
        return channelService.getMember(device);
    }


}
