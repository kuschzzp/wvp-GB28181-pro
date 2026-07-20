package com.genersoft.iot.vmp.gb28181.service;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.bean.*;
import com.genersoft.iot.vmp.gb28181.controller.bean.Extent;
import com.genersoft.iot.vmp.service.bean.GPSMsgInfo;
import com.genersoft.iot.vmp.streamPush.bean.StreamPush;
import com.github.pagehelper.PageInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IGbChannelService {

    CommonGBChannel queryByDeviceId(String gbDeviceId);

    int add(CommonGBChannel commonGBChannel);

    int delete(int gbId);

    void delete(Collection<Integer> ids);

    int update(CommonGBChannel commonGBChannel);

    int offline(CommonGBChannel commonGBChannel);

    int offline(List<CommonGBChannel> commonGBChannelList, boolean permission);

    int online(CommonGBChannel commonGBChannel);

    int online(List<CommonGBChannel> commonGBChannelList, boolean permission);

    void batchAdd(List<CommonGBChannel> commonGBChannels);

    void updateStatus(List<CommonGBChannel> channelList);

    CommonGBChannel getOne(int id);

    List<IndustryCodeType> getIndustryCodeList();

    List<DeviceType> getDeviceTypeList();

    List<NetworkIdentificationType> getNetworkIdentificationTypeList();

    void reset(int id, List<String> chanelFields);

    PageInfo<CommonGBChannel> queryListByCivilCode(
            int page,
            int count,
            String query,
            Boolean online,
            Integer channelType,
            String civilCode,
            VideoScopeQuery scope);

    PageInfo<CommonGBChannel> queryListByParentId(
            int page,
            int count,
            String query,
            Boolean online,
            Integer channelType,
            String groupDeviceId,
            VideoScopeQuery scope);

    void removeCivilCode(List<Region> allChildren);

    void addChannelToRegion(String civilCode, List<Integer> channelIds);

    void deleteChannelToRegion(
            String civilCode,
            List<Integer> channelIds,
            VideoScopeQuery scope);

    void addChannelToRegionByGbDevice(String civilCode, List<Integer> deviceIds);

    void deleteChannelToRegionByGbDevice(List<Integer> deviceIds);

    void removeParentIdByBusinessGroup(String businessGroup);

    void removeParentIdByGroupList(List<Group> groupList);

    void updateBusinessGroup(String oldBusinessGroup, String newBusinessGroup);

    void updateParentIdGroup(String oldParentId, String newParentId);

    void addChannelToGroup(String parentId, String businessGroup, List<Integer> channelIds);

    void deleteChannelToGroup(
            String parentId,
            String businessGroup,
            List<Integer> channelIds,
            VideoScopeQuery scope);

    void addChannelToGroupByGbDevice(String parentId, String businessGroup, List<Integer> deviceIds);

    void deleteChannelToGroupByGbDevice(List<Integer> deviceIds);

    void batchUpdateForStreamPushRedisMsg(List<CommonGBChannel> commonGBChannels, boolean permission);

    CommonGBChannel queryOneWithPlatform(Integer platformId, String channelDeviceId);

    void updateCivilCode(String oldCivilCode, String newCivilCode);

    List<CommonGBChannel> queryListByStreamPushList(List<StreamPush> streamPushList);

    PageInfo<CommonGBChannel> queryList(
            int page,
            int count,
            String query,
            Boolean online,
            Boolean hasRecordPlan,
            Integer channelType,
            String civilCode,
            String parentDeviceId,
            VideoScopeQuery scope);

    PageInfo<CommonGBChannel> queryListByCivilCodeForUnusual(
            int page,
            int count,
            String query,
            Boolean online,
            Integer channelType,
            VideoScopeQuery scope);

    void clearChannelCivilCode(
            Boolean all,
            List<Integer> channelIds,
            VideoScopeQuery scope);

    PageInfo<CommonGBChannel> queryListByParentForUnusual(
            int page,
            int count,
            String query,
            Boolean online,
            Integer channelType,
            VideoScopeQuery scope);

    void clearChannelParent(
            Boolean all,
            List<Integer> channelIds,
            VideoScopeQuery scope);

    void updateGPSFromGPSMsgInfo(List<GPSMsgInfo> gpsMsgInfoList);

    void updateGPS(List<CommonGBChannel> channelList);

    List<CommonGBChannel> queryListForMap(
            String query,
            Boolean online,
            Boolean hasRecordPlan,
            Integer channelType,
            VideoScopeQuery scope);

    CommonGBChannel queryCommonChannelByDeviceChannel(DeviceChannel channel);

    void resetLevel();

    byte[] getTile(
            int z,
            int x,
            int y,
            String geoCoordSys,
            VideoScopeQuery scope);

    String drawThin(
            Map<Integer, Double> zoomParam,
            Extent extent,
            String geoCoordSys,
            VideoScopeQuery scope);

    DrawThinProcess thinProgress(String id);

    void saveThin(String id);
}
