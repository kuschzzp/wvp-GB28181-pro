package com.genersoft.iot.vmp.vmanager.recordPlan;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.GbChannelAccessAuthorizer;
import com.genersoft.iot.vmp.conf.exception.ControllerException;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.gb28181.service.IDeviceChannelService;
import com.genersoft.iot.vmp.service.IRecordPlanService;
import com.genersoft.iot.vmp.service.bean.RecordPlan;
import com.genersoft.iot.vmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.vmp.vmanager.recordPlan.bean.RecordPlanParam;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Tag(name = "录制计划")
@Slf4j
@RestController
@RequestMapping("/api/record/plan")
@PreAuthorize("@authz.check('video:cloud-record:list')")
public class RecordPlanController {

    private static final String CLOUD_RECORD_LIST_PERMISSION =
            "video:cloud-record:list";
    private static final String CLOUD_RECORD_COLLECT_PERMISSION =
            "video:cloud-record:collect";
    private static final String CLOUD_RECORD_REMOVE_PERMISSION =
            "video:cloud-record:remove";

    @Autowired
    private IRecordPlanService recordPlanService;

    @Autowired
    private IDeviceChannelService deviceChannelService;

    @Autowired
    private AuthorizationFacade authorizationFacade;

    @Autowired
    private GbChannelAccessAuthorizer channelAccessAuthorizer;


    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:collect')")
    @PostMapping("/add")
    @Operation(summary = "添加录制计划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "plan", description = "计划", required = true)
    public void add(@RequestBody RecordPlan plan) {
        if (plan.getPlanItemList() == null || plan.getPlanItemList().isEmpty()) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "添加录制计划时，录制计划不可为空");
        }
        recordPlanService.add(plan);
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:collect')")
    @PostMapping("/link")
    @Operation(summary = "通道关联录制计划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "param", description = "通道关联录制计划", required = true)
    public void link(@RequestBody RecordPlanParam param) {
        if (param.getAllLink() != null) {
            Assert.notNull(param.getPlanId(), "录制计划ID不可为NULL");
            if (param.getAllLink()) {
                recordPlanService.linkAll(
                        param.getPlanId(),
                        authorizationFacade.resolveVideoScopeQuery(
                                CLOUD_RECORD_COLLECT_PERMISSION));
            }else {
                recordPlanService.cleanAll(
                        param.getPlanId(),
                        authorizationFacade.resolveVideoScopeQuery(
                                CLOUD_RECORD_COLLECT_PERMISSION));
            }
            return;
        }

        if (param.getChannelIds() == null && param.getDeviceDbIds() == null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "通道ID和国标设备ID不可都为NULL");
        }

        List<Integer> channelIds = new ArrayList<>();
        if (param.getChannelIds() != null) {
            channelIds.addAll(param.getChannelIds());
        }else {
            List<Integer> chanelIdList = deviceChannelService.queryChaneIdListByDeviceDbIds(param.getDeviceDbIds());
            if (chanelIdList != null && !chanelIdList.isEmpty()) {
                channelIds = chanelIdList;
            }
        }
        channelAccessAuthorizer.requireAllChannels(
                CLOUD_RECORD_COLLECT_PERMISSION, channelIds);
        recordPlanService.link(channelIds, param.getPlanId());
    }

    @ResponseBody
    @GetMapping("/get")
    @Operation(summary = "查询录制计划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "planId", description = "计划ID", required = true)
    public RecordPlan get(Integer planId) {
        if (planId == null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "计划ID不可为NULL");
        }
        requireLinkedChannels(CLOUD_RECORD_LIST_PERMISSION, planId);
        return recordPlanService.get(planId);
    }

    @ResponseBody
    @GetMapping("/query")
    @Operation(summary = "查询录制计划列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "query", description = "检索内容", required = false)
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    public PageInfo<RecordPlan> query(@RequestParam(required = false) String query, @RequestParam Integer page, @RequestParam Integer count) {
        if (query != null && ObjectUtils.isEmpty(query.trim())) {
            query = null;
        }
        return recordPlanService.query(page, count, query);
    }

    @Operation(summary = "分页查询录制计划关联的所有通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页条数", required = true)
    @Parameter(name = "planId", description = "录制计划ID")
    @Parameter(name = "channelType", description = "通道类型， 0：国标设备，1：推流设备，2：拉流代理")
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @Parameter(name = "hasLink", description = "是否已经关联")
    @GetMapping("/channel/list")
    @ResponseBody
    public PageInfo<CommonGBChannel> queryChannelList(int page, int count,
                                                      @RequestParam(required = false) Integer planId,
                                                      @RequestParam(required = false) String query,
                                                      @RequestParam(required = false) Integer channelType,
                                                      @RequestParam(required = false) Boolean online,
                                                      @RequestParam(required = false) Boolean hasLink) {

        Assert.notNull(planId, "录制计划ID不可为NULL");
        if (org.springframework.util.ObjectUtils.isEmpty(query)) {
            query = null;
        }

        return recordPlanService.queryChannelList(
                page,
                count,
                query,
                channelType,
                online,
                planId,
                hasLink,
                authorizationFacade.resolveVideoScopeQuery(
                        CLOUD_RECORD_LIST_PERMISSION));
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:collect')")
    @PostMapping("/update")
    @Operation(summary = "更新录制计划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "plan", description = "计划", required = true)
    public void update(@RequestBody RecordPlan plan) {
        if (plan == null || plan.getId() == 0) {
            throw new ControllerException(ErrorCode.ERROR400);
        }
        requireLinkedChannels(CLOUD_RECORD_COLLECT_PERMISSION, plan.getId());
        recordPlanService.update(plan);
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:cloud-record:remove')")
    @DeleteMapping("/delete")
    @Operation(summary = "删除录制计划", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "planId", description = "计划ID", required = true)
    public void delete(Integer planId) {
        if (planId == null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "计划IDID不可为NULL");
        }
        requireLinkedChannels(CLOUD_RECORD_REMOVE_PERMISSION, planId);
        recordPlanService.delete(planId);
    }

    private void requireLinkedChannels(String permissionCode, Integer planId) {
        List<Integer> channelIds = recordPlanService.getLinkedChannelIds(planId);
        if (channelIds != null && !channelIds.isEmpty()) {
            channelAccessAuthorizer.requireAllChannels(
                    permissionCode, channelIds);
        }
    }

}
