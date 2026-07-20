package com.genersoft.iot.vmp.gb28181.controller;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.common.StreamInfo;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.gb28181.bean.*;
import com.genersoft.iot.vmp.gb28181.controller.bean.*;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelPlayService;
import com.genersoft.iot.vmp.gb28181.service.IGbChannelService;
import com.genersoft.iot.vmp.gb28181.utils.VectorTileCatch;
import com.genersoft.iot.vmp.media.security.PlayableStreamService;
import com.genersoft.iot.vmp.service.bean.ErrorCallback;
import com.genersoft.iot.vmp.service.bean.InviteErrorCode;
import com.genersoft.iot.vmp.utils.DateUtil;
import com.genersoft.iot.vmp.vmanager.bean.AudioTalkResult;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.bean.StreamContent;
import com.genersoft.iot.vmp.vmanager.bean.WVPResult;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.beans.PropertyDescriptor;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Tag(name  = "全局通道管理")
@RestController
@Slf4j
@RequestMapping(value = "/api/common/channel")
@PreAuthorize("@authz.check('video:channel:list')")
public class ChannelController {

    private static final String CHANNEL_LIST_PERMISSION = "video:channel:list";
    private static final String CHANNEL_EDIT_PERMISSION = "video:channel:edit";
    private static final String CHANNEL_PLAY_PERMISSION = "video:channel:play";
    private static final String CHANNEL_STOP_PERMISSION = "video:channel:stop";
    private static final String CHANNEL_TALK_PERMISSION = "video:channel:talk";
    private static final String CHANNEL_BROADCAST_PERMISSION =
            "video:channel:broadcast";
    private static final String RECORD_LIST_PERMISSION = "video:record:list";
    private static final String RECORD_PLAY_PERMISSION = "video:record:play";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private IGbChannelService channelService;

    @Autowired
    private AuthorizationFacade authorizationFacade;

    @Autowired
    private GbChannelAccessAuthorizer channelAccessAuthorizer;

    @Autowired
    private IGbChannelPlayService channelPlayService;

    @Autowired
    private PlayableStreamService playableStreamService;

    @Autowired
    private UserSetting userSetting;

    @Autowired
    private VectorTileCatch vectorTileCatch;


    @Operation(summary = "查询通道信息", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "id", description = "通道的数据库自增Id", required = true)
    @GetMapping(value = "/one")
    public CommonGBChannel getOne(int id){
        channelAccessAuthorizer.requireChannelHidden(
                CHANNEL_LIST_PERMISSION, id);
        return channelService.getOne(id);
    }

    @Operation(summary = "获取行业编码列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @GetMapping("/industry/list")
    public List<IndustryCodeType> getIndustryCodeList(){
        return channelService.getIndustryCodeList();
    }

    @Operation(summary = "获取编码列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @GetMapping("/type/list")
    public List<DeviceType> getDeviceTypeList(){
        return channelService.getDeviceTypeList();
    }

    @Operation(summary = "获取编码列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @GetMapping("/network/identification/list")
    public List<NetworkIdentificationType> getNetworkIdentificationTypeList(){
        return channelService.getNetworkIdentificationTypeList();
    }

    @Operation(summary = "更新通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/update")
    public void update(@RequestBody CommonGBChannel channel){
        Assert.notNull(channel, "通道不能为空");
        Assert.isTrue(channel.getGbId() > 0, "通道ID必须大于零");
        BeanWrapperImpl wrapper = new BeanWrapperImpl(channel);
        int count = 0;
        for (PropertyDescriptor pd : wrapper.getPropertyDescriptors()) {
            String name = pd.getName();
            if ("class".equals(name)) continue;
            if (pd.getReadMethod() == null) continue;
            Object val = wrapper.getPropertyValue(name);
            if (val != null) count++;
        }
        Assert.isTrue(count > 1, "未进行任何修改");
        channelAccessAuthorizer.requireChannel(
                CHANNEL_EDIT_PERMISSION, channel.getGbId());
        channelService.update(channel);
    }


    @Operation(summary = "重置国标通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/reset")
    public void reset(@RequestBody ResetParam param){
        Assert.notNull(param.getId(), "通道ID不能为空");
        Assert.notEmpty(param.getChanelFields(), "待重置字段不可以空");
        channelAccessAuthorizer.requireChannel(
                CHANNEL_EDIT_PERMISSION, param.getId());
        channelService.reset(param.getId(), param.getChanelFields());
    }

    @Operation(summary = "增加通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/add")
    public CommonGBChannel add(@RequestBody CommonGBChannel channel){
        channelService.add(channel);
        return channel;
    }

    @Operation(summary = "获取通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "hasRecordPlan", description = "是否已设置录制计划")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @Parameter(name = "civilCode", description = "行政区划")
    @Parameter(name = "parentDeviceId", description = "父节点编码")
    @GetMapping("/list")
    public PageInfo<CommonGBChannel> queryList(int page, int count,
                                                          @RequestParam(required = false) String query,
                                                          @RequestParam(required = false) Boolean online,
                                                          @RequestParam(required = false) Boolean hasRecordPlan,
                                                          @RequestParam(required = false) Integer channelType,
                                                          @RequestParam(required = false) String civilCode,
                                                          @RequestParam(required = false) String parentDeviceId){
        if (ObjectUtils.isEmpty(query)){
            query = null;
        }
        if (ObjectUtils.isEmpty(civilCode)){
            civilCode = null;
        }
        if (ObjectUtils.isEmpty(parentDeviceId)){
            parentDeviceId = null;
        }
        return channelService.queryList(
                page,
                count,
                query,
                online,
                hasRecordPlan,
                channelType,
                civilCode,
                parentDeviceId,
                authorizationFacade.resolveVideoScopeQuery(CHANNEL_LIST_PERMISSION));
    }

    @Operation(summary = "获取关联行政区划通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @Parameter(name = "civilCode", description = "行政区划")
    @GetMapping("/civilcode/list")
    public PageInfo<CommonGBChannel> queryListByCivilCode(int page, int count,
                                               @RequestParam(required = false) String query,
                                               @RequestParam(required = false) Boolean online,
                                               @RequestParam(required = false) Integer channelType,
                                               @RequestParam(required = false) String civilCode){
        if (ObjectUtils.isEmpty(query)){
            query = null;
        }
        return channelService.queryListByCivilCode(
                page,
                count,
                query,
                online,
                channelType,
                civilCode,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }


    @Operation(summary = "存在行政区划但无法挂载的通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @GetMapping("/civilCode/unusual/list")
    public PageInfo<CommonGBChannel> queryListByCivilCodeForUnusual(int page, int count,
                                                          @RequestParam(required = false) String query,
                                                          @RequestParam(required = false) Boolean online,
                                                          @RequestParam(required = false) Integer channelType){
        if (ObjectUtils.isEmpty(query)){
            query = null;
        }
        return channelService.queryListByCivilCodeForUnusual(
                page,
                count,
                query,
                online,
                channelType,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }


    @Operation(summary = "存在父节点编号但无法挂载的通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @GetMapping("/parent/unusual/list")
    public PageInfo<CommonGBChannel> queryListByParentForUnusual(int page, int count,
                                                          @RequestParam(required = false) String query,
                                                          @RequestParam(required = false) Boolean online,
                                                          @RequestParam(required = false) Integer channelType){
        if (ObjectUtils.isEmpty(query)){
            query = null;
        }
        return channelService.queryListByParentForUnusual(
                page,
                count,
                query,
                online,
                channelType,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @Operation(summary = "清除存在行政区划但无法挂载的通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "param", description = "清理参数， all为true清理所有异常数据。 否则按照传入的设备Id清理", required = true)
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/civilCode/unusual/clear")
    public void clearChannelCivilCode(@RequestBody ChannelToRegionParam param){
        Assert.isTrue(Boolean.TRUE.equals(param.getAll())
                || !ObjectUtils.isEmpty(param.getChannelIds()), "参数异常");
        requireChannelsIfPresent(param.getChannelIds());
        channelService.clearChannelCivilCode(
                param.getAll(),
                param.getChannelIds(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_EDIT_PERMISSION));
    }

    @Operation(summary = "清除存在分组节点但无法挂载的通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "param", description = "清理参数， all为true清理所有异常数据。 否则按照传入的设备Id清理", required = true)
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/parent/unusual/clear")
    public void clearChannelParent(@RequestBody ChannelToRegionParam param){
        Assert.isTrue(Boolean.TRUE.equals(param.getAll())
                || !ObjectUtils.isEmpty(param.getChannelIds()), "参数异常");
        requireChannelsIfPresent(param.getChannelIds());
        channelService.clearChannelParent(
                param.getAll(),
                param.getChannelIds(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_EDIT_PERMISSION));
    }

    @Operation(summary = "获取关联业务分组通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @Parameter(name = "groupDeviceId", description = "业务分组下的父节点ID")
    @GetMapping("/parent/list")
    public PageInfo<CommonGBChannel> queryListByParentId(int page, int count,
                                               @RequestParam(required = false) String query,
                                               @RequestParam(required = false) Boolean online,
                                               @RequestParam(required = false) Integer channelType,
                                               @RequestParam(required = false) String groupDeviceId){
        if (ObjectUtils.isEmpty(query)){
            query = null;
        }
        return channelService.queryListByParentId(
                page,
                count,
                query,
                online,
                channelType,
                groupDeviceId,
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION));
    }

    @Operation(summary = "通道设置行政区划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/region/add")
    public void addChannelToRegion(@RequestBody ChannelToRegionParam param){
        Assert.notEmpty(param.getChannelIds(),"通道ID不可为空");
        Assert.hasLength(param.getCivilCode(),"未添加行政区划");
        channelAccessAuthorizer.requireAllChannels(
                CHANNEL_EDIT_PERMISSION, param.getChannelIds());
        channelService.addChannelToRegion(param.getCivilCode(), param.getChannelIds());
    }

    @Operation(summary = "通道删除行政区划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/region/delete")
    public void deleteChannelToRegion(@RequestBody ChannelToRegionParam param){
        Assert.isTrue(!ObjectUtils.isEmpty(param.getChannelIds())
                || !ObjectUtils.isEmpty(param.getCivilCode()),"参数异常");
        requireChannelsIfPresent(param.getChannelIds());
        channelService.deleteChannelToRegion(
                param.getCivilCode(),
                param.getChannelIds(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_EDIT_PERMISSION));
    }

    @Operation(summary = "通道设置行政区划-根据国标设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/region/device/add")
    public void addChannelToRegionByGbDevice(@RequestBody ChannelToRegionByGbDeviceParam param){
        Assert.notEmpty(param.getDeviceIds(),"参数异常");
        Assert.hasLength(param.getCivilCode(),"未添加行政区划");
        channelAccessAuthorizer.requireAllGbDevices(
                CHANNEL_EDIT_PERMISSION, param.getDeviceIds());
        channelService.addChannelToRegionByGbDevice(param.getCivilCode(), param.getDeviceIds());
    }

    @Operation(summary = "通道删除行政区划-根据国标设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/region/device/delete")
    public void deleteChannelToRegionByGbDevice(@RequestBody ChannelToRegionByGbDeviceParam param){
        Assert.notEmpty(param.getDeviceIds(),"参数异常");
        channelAccessAuthorizer.requireAllGbDevices(
                CHANNEL_EDIT_PERMISSION, param.getDeviceIds());
        channelService.deleteChannelToRegionByGbDevice(param.getDeviceIds());
    }

    @Operation(summary = "通道设置业务分组", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/group/add")
    public void addChannelToGroup(@RequestBody ChannelToGroupParam param){
        Assert.notEmpty(param.getChannelIds(),"通道ID不可为空");
        Assert.hasLength(param.getParentId(),"未添加上级分组编号");
        Assert.hasLength(param.getBusinessGroup(),"未添加业务分组");
        channelAccessAuthorizer.requireAllChannels(
                CHANNEL_EDIT_PERMISSION, param.getChannelIds());
        channelService.addChannelToGroup(param.getParentId(), param.getBusinessGroup(), param.getChannelIds());
    }

    @Operation(summary = "通道删除业务分组", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/group/delete")
    public void deleteChannelToGroup(@RequestBody ChannelToGroupParam param){
        Assert.isTrue(!ObjectUtils.isEmpty(param.getChannelIds())
                || (!ObjectUtils.isEmpty(param.getParentId()) && !ObjectUtils.isEmpty(param.getBusinessGroup())),
                "参数异常");
        requireChannelsIfPresent(param.getChannelIds());
        channelService.deleteChannelToGroup(
                param.getParentId(),
                param.getBusinessGroup(),
                param.getChannelIds(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_EDIT_PERMISSION));
    }

    @Operation(summary = "通道设置业务分组-根据国标设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/group/device/add")
    public void addChannelToGroupByGbDevice(@RequestBody ChannelToGroupByGbDeviceParam param){
        Assert.notEmpty(param.getDeviceIds(),"参数异常");
        Assert.hasLength(param.getParentId(),"未添加上级分组编号");
        Assert.hasLength(param.getBusinessGroup(),"未添加业务分组");
        channelAccessAuthorizer.requireAllGbDevices(
                CHANNEL_EDIT_PERMISSION, param.getDeviceIds());
        channelService.addChannelToGroupByGbDevice(param.getParentId(), param.getBusinessGroup(), param.getDeviceIds());
    }

    @Operation(summary = "通道删除业务分组-根据国标设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/group/device/delete")
    public void deleteChannelToGroupByGbDevice(@RequestBody ChannelToGroupByGbDeviceParam param){
        Assert.notEmpty(param.getDeviceIds(),"参数异常");
        channelAccessAuthorizer.requireAllGbDevices(
                CHANNEL_EDIT_PERMISSION, param.getDeviceIds());
        channelService.deleteChannelToGroupByGbDevice(param.getDeviceIds());
    }

    @Operation(summary = "播放通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:play')")
    @GetMapping("/play")
    public DeferredResult<WVPResult<StreamContent>> play(HttpServletRequest request,  Integer channelId){
        requireChannel(CHANNEL_PLAY_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");

        DeferredResult<WVPResult<StreamContent>> result = new DeferredResult<>(userSetting.getPlayTimeout().longValue());

        ErrorCallback<StreamInfo> callback = (code, msg, streamInfo) -> {
            if (code == InviteErrorCode.SUCCESS.getCode()) {
                WVPResult<StreamContent> wvpResult = WVPResult.success();
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
                    if (!ObjectUtils.isEmpty(streamInfo.getMediaServer().getTranscodeSuffix())
                            && !"null".equalsIgnoreCase(streamInfo.getMediaServer().getTranscodeSuffix())) {
                        streamInfo.setStream(streamInfo.getStream() + "_" + streamInfo.getMediaServer().getTranscodeSuffix());
                    }
                    wvpResult.setData(playableStreamService.publish(
                            streamInfo,
                            VideoResourceType.CHANNEL,
                            channelId.longValue()));
                }else {
                    wvpResult.setCode(code);
                    wvpResult.setMsg(msg);
                }
                result.setResult(wvpResult);
            }else {
                result.setResult(WVPResult.fail(code, msg));
            }
        };
        channelPlayService.play(channel, null, userSetting.getRecordSip(), callback);
        return result;
    }

    @Operation(summary = "停止播放通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:stop')")
    @GetMapping("/play/stop")
    public void stopPlay(Integer channelId){
        requireChannel(CHANNEL_STOP_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.stopPlay(channel);
    }

    @Operation(summary = "开始对讲", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:talk')")
    @GetMapping("/talk/start")
    public AudioTalkResult startTalk(Integer channelId){
        requireChannel(CHANNEL_TALK_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        return channelPlayService.startTalk(channel);
    }

    @Operation(summary = "停止对讲", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:talk')")
    @GetMapping("/talk/stop")
    public void stopTalk(Integer channelId){
        requireChannel(CHANNEL_TALK_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.stopTalk(channel);
    }

    @Operation(summary = "开始喊话", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:broadcast')")
    @GetMapping("/broadcast/start")
    public AudioTalkResult startBroadcast(Integer channelId){
        requireChannel(CHANNEL_BROADCAST_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        return channelPlayService.startBroadcast(channel);
    }

    @Operation(summary = "停止喊话", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:broadcast')")
    @GetMapping("/broadcast/stop")
    public void stopBroadcast(Integer channelId){
        requireChannel(CHANNEL_BROADCAST_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.stopBroadcast(channel);
    }

    @Operation(summary = "录像查询", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "startTime", description = "开始时间", required = true)
    @Parameter(name = "endTime", description = "结束时间", required = true)
    @PreAuthorize("@authz.check('video:record:list')")
    @GetMapping("/playback/query")
    public DeferredResult<WVPResult<List<CommonRecordInfo>>> queryRecord(Integer channelId, String startTime, String endTime){

        requireChannel(RECORD_LIST_PERMISSION, channelId);
        DeferredResult<WVPResult<List<CommonRecordInfo>>> result = new DeferredResult<>(Long.valueOf(userSetting.getRecordInfoTimeout()), TimeUnit.MILLISECONDS);
        if (!DateUtil.verification(startTime, DateUtil.formatter)){
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "startTime格式为" + DateUtil.PATTERN);
        }
        if (!DateUtil.verification(endTime, DateUtil.formatter)){
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "endTime格式为" + DateUtil.PATTERN);
        }
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");

        channelPlayService.queryRecord(channel, startTime, endTime, (code, msg, data) -> {
            WVPResult<List<CommonRecordInfo>> wvpResult = new WVPResult<>();
            wvpResult.setCode(code);
            wvpResult.setMsg(msg);
            wvpResult.setData(data);
            result.setResult(wvpResult);
        });
        result.onTimeout(()->{
            WVPResult<List<CommonRecordInfo>> wvpResult = new WVPResult<>();
            wvpResult.setCode(ErrorCode.ERROR100.getCode());
            wvpResult.setMsg("timeout");
            result.setResult(wvpResult);
        });
        return result;
    }

    @Operation(summary = "录像回放", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "startTime", description = "开始时间", required = true)
    @Parameter(name = "endTime", description = "结束时间", required = true)
    @PreAuthorize("@authz.check('video:record:play')")
    @GetMapping("/playback")
    public DeferredResult<WVPResult<StreamContent>> playback(HttpServletRequest request, Integer channelId, String startTime, String endTime){
        requireChannel(RECORD_PLAY_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");

        DeferredResult<WVPResult<StreamContent>> result = new DeferredResult<>(userSetting.getPlayTimeout().longValue());

        ErrorCallback<StreamInfo> callback = (code, msg, streamInfo) -> {
            if (code == InviteErrorCode.SUCCESS.getCode()) {
                WVPResult<StreamContent> wvpResult = WVPResult.success();
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
                    if (!ObjectUtils.isEmpty(streamInfo.getMediaServer().getTranscodeSuffix())
                            && !"null".equalsIgnoreCase(streamInfo.getMediaServer().getTranscodeSuffix())) {
                        streamInfo.setStream(streamInfo.getStream() + "_" + streamInfo.getMediaServer().getTranscodeSuffix());
                    }
                    wvpResult.setData(playableStreamService.publish(
                            streamInfo,
                            VideoResourceType.DEVICE_RECORD,
                            channelId.longValue()));
                }else {
                    wvpResult.setCode(code);
                    wvpResult.setMsg(msg);
                }

                result.setResult(wvpResult);
            }else {
                result.setResult(WVPResult.fail(code, msg));
            }
        };
        channelPlayService.playback(channel, DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime),
                DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime), callback);
        return result;
    }

    @Operation(summary = "停止录像回放", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @PreAuthorize("@authz.check('video:record:play')")
    @GetMapping("/playback/stop")
    public void stopPlayback(Integer channelId, String stream){
        requireChannel(RECORD_PLAY_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.stopPlayback(channel, stream);
    }

    @Operation(summary = "暂停录像回放", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @PreAuthorize("@authz.check('video:record:play')")
    @GetMapping("/playback/pause")
    public void pausePlayback(Integer channelId, String stream){
        requireChannel(RECORD_PLAY_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.playbackPause(channel, stream);
    }

    @Operation(summary = "恢复录像回放", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @PreAuthorize("@authz.check('video:record:play')")
    @GetMapping("/playback/resume")
    public void resumePlayback(Integer channelId, String stream){
        requireChannel(RECORD_PLAY_PERMISSION, channelId);
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.playbackResume(channel, stream);
    }

    @Operation(summary = "拖动录像回放", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "seekTime", description = "将要播放的时间", required = true)
    @PreAuthorize("@authz.check('video:record:play')")
    @GetMapping("/playback/seek")
    public void seekPlayback(Integer channelId, String stream, Long seekTime){
        requireChannel(RECORD_PLAY_PERMISSION, channelId);
        Assert.notNull(seekTime,"参数异常");
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.playbackSeek(channel, stream, seekTime);
    }

    @Operation(summary = "拖动录像回放", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channelId", description = "通道ID", required = true)
    @Parameter(name = "stream", description = "流ID", required = true)
    @Parameter(name = "speed", description = "倍速", required = true)
    @PreAuthorize("@authz.check('video:record:play')")
    @GetMapping("/playback/speed")
    public void seekPlayback(Integer channelId, String stream, Double speed){
        requireChannel(RECORD_PLAY_PERMISSION, channelId);
        Assert.notNull(speed,"参数异常");
        CommonGBChannel channel = channelService.getOne(channelId);
        Assert.notNull(channel, "通道不存在");
        channelPlayService.playbackSpeed(channel, stream, speed);
    }

    private void requireChannel(String permissionCode, Integer channelId) {
        Assert.notNull(channelId, "参数异常");
        channelAccessAuthorizer.requireChannel(
                permissionCode, channelId.longValue());
    }

    private void requireChannelsIfPresent(List<Integer> channelIds) {
        if (!ObjectUtils.isEmpty(channelIds)) {
            channelAccessAuthorizer.requireAllChannels(
                    CHANNEL_EDIT_PERMISSION, channelIds);
        }
    }

    @Operation(summary = "为地图获取通道列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "hasRecordPlan", description = "是否已设置录制计划")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @Parameter(name = "geoCoordSys", description = "地理坐标系， WGS84/GCJ02")
    @GetMapping("/map/list")
    public List<CommonGBChannel> queryListForMap(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Boolean online,
            @RequestParam(required = false) Boolean hasRecordPlan,
            @RequestParam(required = false) Integer channelType){
        if (ObjectUtils.isEmpty(query)){
            query = null;
        }
        return channelService.queryListForMap(
                query,
                online,
                hasRecordPlan,
                channelType,
                authorizationFacade.resolveVideoScopeQuery(CHANNEL_LIST_PERMISSION));
    }

    @Operation(summary = "为地图去除抽稀结果", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/map/reset-level")
    public void resetLevel(){
        requireAllScope(CHANNEL_EDIT_PERMISSION);
        channelService.resetLevel();
    }

    @Operation(summary = "执行抽稀", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @PreAuthorize("@authz.check('video:channel:edit')")
    @PostMapping("/map/thin/draw")
    public String drawThin(@RequestBody DrawThinParam param){
        if(param == null || param.getZoomParam() == null || param.getZoomParam().isEmpty()) {
            throw new ControllerException(ErrorCode.ERROR400);
        }
        return channelService.drawThin(
                param.getZoomParam(),
                param.getExtent(),
                param.getGeoCoordSys(),
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_EDIT_PERMISSION));
    }

    @Operation(summary = "清除未保存的抽稀结果", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "id", description = "抽稀ID", required = true)
    @PreAuthorize("@authz.check('video:channel:edit')")
    @GetMapping("/map/thin/clear")
    public void clearThin(String id){
        requireOwnedThinId(
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_EDIT_PERMISSION),
                id);
        vectorTileCatch.remove(id);
    }

    @Operation(summary = "保存的抽稀结果", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "id", description = "抽稀ID", required = true)
    @PreAuthorize("@authz.check('video:channel:edit')")
    @GetMapping("/map/thin/save")
    public void saveThin(String id){
        VideoScopeQuery scope = requireAllScope(CHANNEL_EDIT_PERMISSION);
        requireOwnedThinId(scope, id);
        channelService.saveThin(id);
    }

    @Operation(summary = "获取抽稀执行的进度", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "id", description = "抽稀ID", required = true)
    @GetMapping("/map/thin/progress")
    public DrawThinProcess thinProgress(String id){
        requireOwnedThinId(
                authorizationFacade.resolveVideoScopeQuery(
                        CHANNEL_LIST_PERMISSION),
                id);
        return channelService.thinProgress(id);
    }

    @Operation(summary = "为地图提供标准mvt图层", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @GetMapping(value = "/map/tile/{z}/{x}/{y}", produces = "application/x-protobuf")
    @Parameter(name = "geoCoordSys", description = "地理坐标系， WGS84/GCJ02")
    public ResponseEntity<byte[]> getTile(@PathVariable int z, @PathVariable int x, @PathVariable int y, String geoCoordSys){

        try {
            byte[] mvt = channelService.getTile(
                    z,
                    x,
                    y,
                    geoCoordSys,
                    authorizationFacade.resolveVideoScopeQuery(
                            CHANNEL_LIST_PERMISSION));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/x-protobuf"));
            if (mvt == null) {
                headers.setContentLength(0);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }
            headers.setContentLength(mvt.length);
            return new ResponseEntity<>(mvt, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("构建矢量瓦片失败： z: {}, x: {}, y:{}", z, x, y, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Operation(summary = "为地图提供经过抽稀的标准mvt图层", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @GetMapping(value = "/map/thin/tile/{z}/{x}/{y}", produces = "application/x-protobuf")
    @Parameter(name = "geoCoordSys", description = "地理坐标系， WGS84/GCJ02")
    @Parameter(name = "thinId", description = "抽稀结果ID")
    public ResponseEntity<byte[]> getThinTile(@PathVariable int z, @PathVariable int x, @PathVariable int y,
                                              String geoCoordSys, @RequestParam(required = false) String thinId){
        VideoScopeQuery scope = authorizationFacade.resolveVideoScopeQuery(
                CHANNEL_LIST_PERMISSION);
        if (ObjectUtils.isEmpty(thinId)) {
            if (!scope.all()) {
                return tileResponse(channelService.getTile(
                        z, x, y, geoCoordSys, scope));
            }
            thinId = "DEFAULT";
        } else {
            requireOwnedThinId(scope, thinId);
        }
        String coordinateSystem = ObjectUtils.isEmpty(geoCoordSys)
                ? "WGS84"
                : geoCoordSys.toUpperCase();
        String catchKey = z + "_" + x + "_" + y + "_"
                + coordinateSystem;
        byte[] mvt = vectorTileCatch.getVectorTile(thinId, catchKey);
        return tileResponse(mvt);
    }

    private ResponseEntity<byte[]> tileResponse(byte[] mvt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-protobuf"));
        if (mvt == null) {
            headers.setContentLength(0);
            return ResponseEntity.status(HttpStatus.OK).body(null);
        }

        headers.setContentLength(mvt.length);
        return new ResponseEntity<>(mvt, headers, HttpStatus.OK);
    }

    private VideoScopeQuery requireAllScope(String permissionCode) {
        VideoScopeQuery scope = authorizationFacade.resolveVideoScopeQuery(
                permissionCode);
        if (!scope.all()) {
            throw new AccessDeniedException("该操作需要全部视频资源范围");
        }
        return scope;
    }

    private void requireOwnedThinId(VideoScopeQuery scope, String id) {
        Assert.hasText(id, "抽稀ID不能为空");
        if (!scope.all()
                && !id.startsWith(scope.principalId() + "_")) {
            throw new AccessDeniedException("无权访问该抽稀任务");
        }
    }


}
