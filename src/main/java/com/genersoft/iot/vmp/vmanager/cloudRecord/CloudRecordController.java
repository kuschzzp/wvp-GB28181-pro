package com.genersoft.iot.vmp.vmanager.cloudRecord;

import com.alibaba.fastjson2.JSONArray;
import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.DerivedResourceAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import com.genersoft.iot.vmp.media.security.PlayableStreamService;
import com.genersoft.iot.vmp.media.service.IMediaServerService;
import com.genersoft.iot.vmp.service.ICloudRecordService;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.service.bean.ErrorCallback;
import com.genersoft.iot.vmp.service.bean.InviteErrorCode;
import com.genersoft.iot.vmp.streamPush.bean.BatchRemoveParam;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

@SuppressWarnings("rawtypes")
@Tag(name = "云端录像接口")
@Slf4j
@RestController
@RequestMapping("/api/cloud/record")
@PreAuthorize("@authz.check('video:cloud-record:list')")
public class CloudRecordController {

    private static final String CLOUD_RECORD_LIST_PERMISSION = "video:cloud-record:list";
    private static final String CLOUD_RECORD_DOWNLOAD_PERMISSION =
            "video:cloud-record:download";
    private static final String CLOUD_RECORD_COLLECT_PERMISSION =
            "video:cloud-record:collect";
    private static final String CLOUD_RECORD_PLAY_PERMISSION =
            "video:cloud-record:play";
    private static final String CLOUD_RECORD_REMOVE_PERMISSION =
            "video:cloud-record:remove";

    @Autowired
    private ICloudRecordService cloudRecordService;

    @Autowired
    private AuthorizationFacade authorizationFacade;

    @Autowired
    private DerivedResourceAccessAuthorizer derivedResourceAccessAuthorizer;

    @Autowired
    private GbChannelAccessAuthorizer channelAccessAuthorizer;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private PlayableStreamService playableStreamService;

    @Autowired
    private DownloadTicketService downloadTicketService;

    @Autowired
    private UserSetting userSetting;


    @ResponseBody
    @GetMapping("/date/list")
    @Operation(summary = "查询存在云端录像的日期", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "year", description = "年，置空则查询当年", required = false)
    @Parameter(name = "month", description = "月，置空则查询当月", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID，置空则查询全部", required = false)
    public List<String> openRtpServer(
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) String mediaServerId

    ) {
        log.info("[云端录像] 查询存在云端录像的日期 app->{}, stream->{}, mediaServerId->{}, year->{}, month->{}", app, stream, mediaServerId, year, month);
        Calendar calendar = Calendar.getInstance();
        if (ObjectUtils.isEmpty(year)) {
            year = calendar.get(Calendar.YEAR);
        }
        if (ObjectUtils.isEmpty(month)) {
            month = calendar.get(Calendar.MONTH) + 1;
        }
        List<MediaServer> mediaServers;
        if (!ObjectUtils.isEmpty(mediaServerId)) {
            mediaServers = new ArrayList<>();
            MediaServer mediaServer = mediaServerService.getOne(mediaServerId);
            if (mediaServer == null) {
                throw new ControllerException(ErrorCode.ERROR100.getCode(), "未找到流媒体: " + mediaServerId);
            }
            mediaServers.add(mediaServer);
        } else {
            mediaServers = mediaServerService.getAllOnlineList();
        }
        if (mediaServers.isEmpty()) {
            return new ArrayList<>();
        }

        return cloudRecordService.getDateList(
                app,
                stream,
                year,
                month,
                mediaServers,
                authorizationFacade.resolveVideoScopeQuery(CLOUD_RECORD_LIST_PERMISSION));
    }

    @ResponseBody
    @GetMapping("/list")
    @Operation(summary = "分页查询云端录像", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "query", description = "检索内容", required = false)
    @Parameter(name = "app", description = "应用名", required = false)
    @Parameter(name = "stream", description = "流ID", required = false)
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "startTime", description = "开始时间(yyyy-MM-dd HH:mm:ss)", required = false)
    @Parameter(name = "endTime", description = "结束时间(yyyy-MM-dd HH:mm:ss)", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID，置空则查询全部流媒体", required = false)
    @Parameter(name = "callId", description = "每次录像的唯一标识，置空则查询全部流媒体", required = false)
    @Parameter(name = "ascOrder", description = "是否升序排序， 升序： true， 降序： false", required = false)
    public PageInfo<CloudRecordItem> openRtpServer(@RequestParam(required = false) String query,
                                                   @RequestParam(required = false) String app,
                                                   @RequestParam(required = false) String stream,
                                                   @RequestParam int page,
                                                   @RequestParam int count,
                                                   @RequestParam(required = false) String startTime,
                                                   @RequestParam(required = false) String endTime,
                                                   @RequestParam(required = false) String mediaServerId,
                                                   @RequestParam(required = false) String callId,
                                                   @RequestParam(required = false) Boolean ascOrder

    ) {
        log.info("[云端录像] 查询 app->{}, stream->{}, mediaServerId->{}, page->{}, count->{}, startTime->{}, endTime->{}, callId->{}", app, stream, mediaServerId, page, count, startTime, endTime, callId);

        List<MediaServer> mediaServers;
        if (!ObjectUtils.isEmpty(mediaServerId)) {
            mediaServers = new ArrayList<>();
            MediaServer mediaServer = mediaServerService.getOne(mediaServerId);
            if (mediaServer == null) {
                throw new ControllerException(ErrorCode.ERROR100.getCode(), "未找到流媒体: " + mediaServerId);
            }
            mediaServers.add(mediaServer);
        } else {
            mediaServers = null;
        }
        if (query != null && ObjectUtils.isEmpty(query.trim())) {
            query = null;
        }
        if (app != null && ObjectUtils.isEmpty(app.trim())) {
            app = null;
        }
        if (stream != null && ObjectUtils.isEmpty(stream.trim())) {
            stream = null;
        }
        if (startTime != null && ObjectUtils.isEmpty(startTime.trim())) {
            startTime = null;
        }
        if (endTime != null && ObjectUtils.isEmpty(endTime.trim())) {
            endTime = null;
        }
        if (callId != null && ObjectUtils.isEmpty(callId.trim())) {
            callId = null;
        }
        return cloudRecordService.getList(
                page,
                count,
                query,
                app,
                stream,
                startTime,
                endTime,
                mediaServers,
                callId,
                ascOrder,
                authorizationFacade.resolveVideoScopeQuery(CLOUD_RECORD_LIST_PERMISSION));
    }

    @ResponseBody
    @GetMapping("/task/add")
    @Operation(summary = "添加合并任务")
    @Parameter(name = "app", description = "应用名", required = false)
    @Parameter(name = "stream", description = "流ID", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "startTime", description = "鉴权ID", required = false)
    @Parameter(name = "endTime", description = "鉴权ID", required = false)
    @Parameter(name = "callId", description = "鉴权ID", required = false)
    @Parameter(name = "remoteHost", description = "返回地址时的远程地址", required = false)
    public String addTask(HttpServletRequest request, @RequestParam(required = false) String app, @RequestParam(required = false) String stream, @RequestParam(required = false) String mediaServerId, @RequestParam(required = false) String startTime, @RequestParam(required = false) String endTime, @RequestParam(required = false) String callId, @RequestParam(required = false) String remoteHost) {
        MediaServer mediaServer;
        if (mediaServerId == null) {
            mediaServer = mediaServerService.getDefaultMediaServer();
        } else {
            mediaServer = mediaServerService.getOne(mediaServerId);
        }
        if (mediaServer == null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "未找到可用的流媒体");
        } else {
            if (remoteHost == null) {
                remoteHost = request.getScheme() + "://" + mediaServer.getIp() + ":" + mediaServer.getRecordAssistPort();
            }
        }
        return cloudRecordService.addTask(
                app, stream, mediaServer, startTime, endTime, callId,
                remoteHost, mediaServerId != null,
                authorizationFacade.resolveVideoScopeQuery(
                        CLOUD_RECORD_LIST_PERMISSION));
    }

    @ResponseBody
    @GetMapping("/task/list")
    @Operation(summary = "查询合并任务")
    @Parameter(name = "taskId", description = "任务Id", required = false)
    @Parameter(name = "mediaServerId", description = "流媒体ID", required = false)
    @Parameter(name = "isEnd", description = "是否结束", required = false)
    public JSONArray queryTaskList(HttpServletRequest request, @RequestParam(required = false) String app, @RequestParam(required = false) String stream, @RequestParam(required = false) String callId, @RequestParam(required = false) String taskId, @RequestParam(required = false) String mediaServerId, @RequestParam(required = false) Boolean isEnd) {
        if (ObjectUtils.isEmpty(mediaServerId)) {
            mediaServerId = null;
        }

        return cloudRecordService.queryTask(
                app,
                stream,
                callId,
                taskId,
                mediaServerId,
                isEnd,
                request.getScheme(),
                authorizationFacade.resolveVideoScopeQuery(
                        CLOUD_RECORD_LIST_PERMISSION));
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:collect')")
    @GetMapping("/collect/add")
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
    @GetMapping("/collect/delete")
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
    @GetMapping("/play/path")
    @Operation(summary = "获取播放地址")
    @Parameter(name = "recordId", description = "录像记录的ID", required = true)
    public DownloadTicketResponse getPlayUrlPath(
            @RequestParam(required = true) Integer recordId) {
        derivedResourceAccessAuthorizer.requireRecord(
                CLOUD_RECORD_DOWNLOAD_PERMISSION, recordId);
        return downloadTicketService.issue(DownloadTicketRequest.single(
                DownloadResourceType.CLOUD_RECORD, recordId));
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:play')")
    @GetMapping("/loadRecord")
    @Operation(summary = "加载录像文件形成播放地址")
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "cloudRecordId", description = "云端录像ID", required = true)
    public DeferredResult<WVPResult<StreamContent>> loadRecord(
            HttpServletRequest request,
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream,
            @RequestParam(required = true) int cloudRecordId
            ) {
        derivedResourceAccessAuthorizer.requireRecord(
                CLOUD_RECORD_PLAY_PERMISSION, cloudRecordId);
        DeferredResult<WVPResult<StreamContent>> result = new DeferredResult<>();

        result.onTimeout(()->{
            log.info("[加载录像文件超时] app={}, stream={}, cloudRecordId={}", app, stream, cloudRecordId);
            WVPResult<StreamContent> wvpResult = new WVPResult<>();
            wvpResult.setCode(ErrorCode.ERROR100.getCode());
            wvpResult.setMsg("加载录像文件超时");
            result.setResult(wvpResult);
        });

        ErrorCallback<StreamInfo> callback = (code, msg, streamInfo) -> {

            WVPResult<StreamContent> wvpResult = new WVPResult<>();
            if (code == InviteErrorCode.SUCCESS.getCode()) {
                wvpResult.setCode(ErrorCode.SUCCESS.getCode());
                wvpResult.setMsg(ErrorCode.SUCCESS.getMsg());

                if (streamInfo != null) {
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
                    if (!org.springframework.util.ObjectUtils.isEmpty(streamInfo.getMediaServer().getTranscodeSuffix()) && !"null".equalsIgnoreCase(streamInfo.getMediaServer().getTranscodeSuffix())) {
                        streamInfo.setStream(streamInfo.getStream() + "_" + streamInfo.getMediaServer().getTranscodeSuffix());
                    }
                    wvpResult.setData(playableStreamService.publish(
                            streamInfo,
                            VideoResourceType.CLOUD_RECORD,
                            cloudRecordId));
                }else {
                    wvpResult.setCode(code);
                    wvpResult.setMsg(msg);
                }
            }else {
                wvpResult.setCode(code);
                wvpResult.setMsg(msg);
            }
            result.setResult(wvpResult);
        };

        cloudRecordService.loadMP4File(app, stream, cloudRecordId, callback);
        return result;
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:play')")
    @GetMapping("/seek")
    @Operation(summary = "定位录像播放到制定位置")
    @Parameter(name = "mediaServerId", description = "使用的节点Id", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "seek", description = "要定位的时间位置，从录像开始的时间算起", required = true)
    public void seekRecord(
            @RequestParam(required = true) String mediaServerId,
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream,
            @RequestParam(required = true) Double seek,
            @RequestParam(required = false) String schema
            ) {
        if (schema == null) {
            schema = "ts";
        }
        channelAccessAuthorizer.requireAppStream(
                CLOUD_RECORD_PLAY_PERMISSION, app, stream);
        cloudRecordService.seekRecord(mediaServerId, app, stream, seek, schema);
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:play')")
    @GetMapping("/speed")
    @Operation(summary = "设置录像播放速度")
    @Parameter(name = "mediaServerId", description = "使用的节点Id", required = true)
    @Parameter(name = "app", description = "应用名", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "speed", description = "要设置的录像倍速", required = true)
    public void setRecordSpeed(
            @RequestParam(required = true) String mediaServerId,
            @RequestParam(required = true) String app,
            @RequestParam(required = true) String stream,
            @RequestParam(required = true) Integer speed,
            @RequestParam(required = false) String schema
    ) {
        if (schema == null) {
            schema = "ts";
        }
        channelAccessAuthorizer.requireAppStream(
                CLOUD_RECORD_PLAY_PERMISSION, app, stream);
        cloudRecordService.setRecordSpeed(mediaServerId, app, stream, speed, schema);
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:remove')")
    @DeleteMapping("/delete")
    @Operation(summary = "删除录像文件")
    @Parameter(name = "ids", description = "文件ID集合", required = true)
    public void deleteFileByIds(@RequestBody BatchRemoveParam ids) {
        derivedResourceAccessAuthorizer.requireAllRecords(
                CLOUD_RECORD_REMOVE_PERMISSION, ids.getIds());
        cloudRecordService.deleteFileByIds(ids.getIds());
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:download')")
    @GetMapping("/download/zip")
    public DownloadTicketResponse downloadZipFileFromUrl(Integer[] ids) {
        if (ids == null || ids.length == 0) {
            throw new IllegalArgumentException("录像 ID 集合不能为空");
        }
        List<Integer> arrayList = java.util.Arrays.asList(ids);
        derivedResourceAccessAuthorizer.requireAllRecords(
                CLOUD_RECORD_DOWNLOAD_PERMISSION, arrayList);
        return downloadTicketService.issue(DownloadTicketRequest.batch(
                DownloadResourceType.CLOUD_RECORD_ZIP,
                arrayList.stream().map(Integer::longValue).toList()));
    }





    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:download')")
    @GetMapping("/zip")
    public DownloadTicketResponse downloadZipFile(
            @RequestParam List<Integer> ids) {
        derivedResourceAccessAuthorizer.requireAllRecords(
                CLOUD_RECORD_DOWNLOAD_PERMISSION, ids);
        return downloadTicketService.issue(DownloadTicketRequest.batch(
                DownloadResourceType.CLOUD_RECORD_ZIP,
                ids.stream().map(Integer::longValue).toList()));
    }
}
