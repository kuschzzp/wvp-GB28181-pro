package com.genersoft.iot.vmp.jt1078.controller;

import com.genersoft.iot.vmp.authorization.AuthorizationFacade;
import com.genersoft.iot.vmp.authorization.video.JtResourceAccessAuthorizer;
import com.genersoft.iot.vmp.conf.security.JwtUtils;
import com.genersoft.iot.vmp.jt1078.bean.JTChannel;
import com.genersoft.iot.vmp.jt1078.bean.JTDevice;
import com.genersoft.iot.vmp.jt1078.service.Ijt1078Service;
import com.github.pagehelper.PageInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;



@Slf4j
@ConditionalOnProperty(value = "jt1078.enable", havingValue = "true")
@RestController
@Tag(name  = "部标终端以及通道管理")
@RequestMapping("/api/jt1078/terminal")
@PreAuthorize("@authz.check('video:jt:list')")
public class JT1078TerminalController {

    private static final String JT_LIST_PERMISSION = "video:jt:list";
    private static final String JT_EDIT_PERMISSION = "video:jt:edit";

    @Resource
    Ijt1078Service service;

    @Resource
    AuthorizationFacade authorizationFacade;

    @Resource
    JtResourceAccessAuthorizer jtResourceAccessAuthorizer;

    @Operation(summary = "JT-分页查询部标设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "query", description = "查询内容")
    @Parameter(name = "online", description = "是否在线")
    @GetMapping("/list")
    public PageInfo<JTDevice> getDevices(int page, int count,
                                         @RequestParam(required = false) String query,
                                         @RequestParam(required = false) Boolean online) {
        return service.getDeviceList(
                page,
                count,
                query,
                online,
                authorizationFacade.resolveVideoScopeQuery(JT_LIST_PERMISSION));
    }

    @Operation(summary = "更新设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "device", description = "设备", required = true)
    @PreAuthorize("@authz.check('video:jt:edit')")
    @PostMapping("/update")
    public void updateDevice(JTDevice device){
        Assert.notNull(device, "设备不可为 NULL");
        Assert.isTrue(device.getId() > 0, "设备 ID 必须大于零");
        Assert.hasText(device.getPhoneNumber(), "设备手机号不可为空");
        jtResourceAccessAuthorizer.requireTerminalId(
                JT_EDIT_PERMISSION, device.getId());
        service.updateDevice(device);
    }

    @Operation(summary = "JT-新增设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "device", description = "设备", required = true)
    @PreAuthorize("@authz.check('video:jt:edit')")
    @PostMapping("/add")
    public void addDevice(JTDevice device){
        assert device.getPhoneNumber() != null;
        String phoneNumber = device.getPhoneNumber().replaceFirst("^0*", "");
        device.setPhoneNumber(phoneNumber);
        service.addDevice(device);
    }
    @Operation(summary = "删除设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "phoneNumber", description = "设备手机号", required = true)
    @PreAuthorize("@authz.check('video:jt:edit')")
    @DeleteMapping("/delete")
    public void addDevice(String phoneNumber){
        Assert.hasText(phoneNumber, "设备手机号不可为空");
        jtResourceAccessAuthorizer.requireTerminal(
                JT_EDIT_PERMISSION, phoneNumber);
        service.deleteDeviceByPhoneNumber(phoneNumber);
    }
    @Operation(summary = "查询设备", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "phoneNumber", description = "设备手机号", required = true)
    @GetMapping("/query")
    public JTDevice getDevice(Integer deviceId){
        Assert.notNull(deviceId, "设备 ID 不可为 NULL");
        jtResourceAccessAuthorizer.requireTerminalId(
                JT_LIST_PERMISSION, deviceId);
        return service.getDeviceById(deviceId);
    }


    @Operation(summary = "JT-查询部标通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "page", description = "当前页", required = true)
    @Parameter(name = "count", description = "每页查询数量", required = true)
    @Parameter(name = "deviceId", description = "设备ID", required = true)
    @Parameter(name = "query", description = "查询内容")
    @GetMapping("/channel/list")
    public PageInfo<JTChannel> getChannels(int page, int count,
                                           @RequestParam(required = true) Integer deviceId,
                                           @RequestParam(required = false) String query) {
        assert deviceId != null;
        return service.getChannelList(
                page,
                count,
                deviceId,
                query,
                authorizationFacade.resolveVideoScopeQuery(JT_LIST_PERMISSION));
    }

    @Operation(summary = "JT-查询单个部标通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "id", description = "通道数据库ID", required = true)
    @GetMapping("/channel/one")
    public JTChannel getChannel(Integer id) {
        Assert.notNull(id, "通道 ID 不可为 NULL");
        jtResourceAccessAuthorizer.requireChannelId(
                JT_LIST_PERMISSION, id);
        return service.getChannelByDbId(id);
    }

    @Operation(summary = "JT-更新通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channel", description = "通道", required = true)
    @PreAuthorize("@authz.check('video:jt:edit')")
    @PostMapping("/channel/update")
    public void updateChannel(@RequestBody JTChannel channel){
        Assert.notNull(channel, "通道不可为 NULL");
        Assert.isTrue(channel.getId() > 0, "通道 ID 必须大于零");
        Assert.notNull(channel.getChannelId(), "通道编号不可为 NULL");
        jtResourceAccessAuthorizer.requireChannelId(
                JT_EDIT_PERMISSION, channel.getId());
        JTChannel existing = service.getChannelByDbId(channel.getId());
        if (existing != null
                && channel.getTerminalDbId() > 0
                && channel.getTerminalDbId() != existing.getTerminalDbId()) {
            jtResourceAccessAuthorizer.requireTerminalId(
                    JT_EDIT_PERMISSION, channel.getTerminalDbId());
        }
        service.updateChannel(channel);
    }

    @Operation(summary = "JT-新增通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "channel", description = "通道", required = true)
    @PreAuthorize("@authz.check('video:jt:edit')")
    @PostMapping("/channel/add")
    public JTChannel addChannel(@RequestBody JTChannel channel){
        Assert.notNull(channel, "通道不可为 NULL");
        Assert.notNull(channel.getChannelId(), "通道编号不可为 NULL");
        Assert.isTrue(
                channel.getTerminalDbId() > 0,
                "所属终端 ID 必须大于零");
        jtResourceAccessAuthorizer.requireTerminalId(
                JT_EDIT_PERMISSION, channel.getTerminalDbId());
        service.addChannel(channel);
        return channel;
    }
    @Operation(summary = "JT-删除通道", security = @SecurityRequirement(name = JwtUtils.HEADER))
    @Parameter(name = "id", description = "通道的数据库ID", required = true)
    @PreAuthorize("@authz.check('video:jt:edit')")
    @DeleteMapping("/channel/delete")
    public void deleteChannel(Integer id){
        Assert.notNull(id, "通道 ID 不可为 NULL");
        jtResourceAccessAuthorizer.requireChannelId(
                JT_EDIT_PERMISSION, id);
        service.deleteChannelById(id);
    }
}
