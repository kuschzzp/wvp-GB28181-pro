package com.genersoft.iot.vmp.vmanager.alarm;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.DerivedResourceAccessAuthorizer;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.media.security.DownloadResourceType;
import com.genersoft.iot.vmp.media.security.DownloadTicketService;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketRequest;
import com.genersoft.iot.vmp.media.security.dto.DownloadTicketResponse;
import com.genersoft.iot.vmp.service.IAlarmService;
import com.genersoft.iot.vmp.service.bean.AlarmType;
import com.genersoft.iot.vmp.service.bean.Alarm;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "报警管理接口")
@Slf4j
@RestController
@RequestMapping("/api/alarm")
@RequiredArgsConstructor
@PreAuthorize("@authz.check('video:alarm:list')")
public class AlarmController {

    private static final String ALARM_LIST_PERMISSION = "video:alarm:list";
    private static final String ALARM_REMOVE_PERMISSION = "video:alarm:remove";
    private static final String ALARM_CLEAR_PERMISSION = "video:alarm:clear";

    private final IAlarmService alarmService;

    private final AuthorizationFacade authorizationFacade;

    private final DerivedResourceAccessAuthorizer derivedResourceAccessAuthorizer;

    private final DownloadTicketService downloadTicketService;

    @ResponseBody
    @GetMapping("/list")
    @Operation(summary = "分页查询报警列表", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "alarmType", description = "报警类型列表，多个类型用逗号分隔")
    @Parameter(name = "beginTime", description = "开始时间，格式：yyyy-MM-dd HH:mm:ss")
    @Parameter(name = "endTime", description = "结束时间，格式：yyyy-MM-dd HH:mm:ss")
    public PageInfo<Alarm> list(@RequestParam Integer page,
                                @RequestParam Integer count,
                                @RequestParam(required = false) List<AlarmType> alarmType,
                                @RequestParam(required = false) String beginTime,
                                @RequestParam(required = false) String endTime) {
        return alarmService.getAlarms(
                page,
                count,
                alarmType,
                beginTime,
                endTime,
                authorizationFacade.resolveVideoScopeQuery(ALARM_LIST_PERMISSION));
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:alarm:remove')")
    @DeleteMapping("/delete")
    @Operation(summary = "删除报警信息", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "ids", description = "报警ID列表", required = true)
    public void delete(@RequestBody List<Long> ids) {
        derivedResourceAccessAuthorizer.requireAllAlarms(
                ALARM_REMOVE_PERMISSION, ids);
        alarmService.deleteAlarmInfo(ids);
    }

    @ResponseBody
    @PreAuthorize("@authz.check('video:alarm:clear')")
    @DeleteMapping("/clear")
    @Operation(summary = "按筛选条件清空报警信息", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "alarmType", description = "报警类型列表，为空则不限类型")
    @Parameter(name = "beginTime", description = "开始时间，格式：yyyy-MM-dd HH:mm:ss")
    @Parameter(name = "endTime", description = "结束时间，格式：yyyy-MM-dd HH:mm:ss")
    public int clear(@RequestParam(required = false) List<AlarmType> alarmType,
                     @RequestParam(required = false) String beginTime,
                     @RequestParam(required = false) String endTime) {
        return alarmService.clearAlarmsByCondition(
                alarmType,
                beginTime,
                endTime,
                authorizationFacade.resolveVideoScopeQuery(
                        ALARM_CLEAR_PERMISSION));
    }

    @GetMapping("/snap/{id}")
    @Operation(summary = "获取报警快照图片")
    @Parameter(name = "id", description = "报警ID", required = true)
    public DownloadTicketResponse snap(@PathVariable Long id) {
        derivedResourceAccessAuthorizer.requireAlarm(
                ALARM_LIST_PERMISSION, id);
        return downloadTicketService.issue(DownloadTicketRequest.single(
                DownloadResourceType.ALARM_SNAPSHOT, id));
    }
}
