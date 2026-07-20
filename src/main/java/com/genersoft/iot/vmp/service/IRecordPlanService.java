package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.bean.CommonGBChannel;
import com.genersoft.iot.vmp.service.bean.RecordPlan;
import com.github.pagehelper.PageInfo;

import java.util.List;

public interface IRecordPlanService {


    RecordPlan get(Integer planId);

    void update(RecordPlan plan);

    void delete(Integer planId);

    PageInfo<RecordPlan> query(Integer page, Integer count, String query);

    void add(RecordPlan plan);

    void link(List<Integer> channelIds, Integer planId);

    PageInfo<CommonGBChannel> queryChannelList(
            int page,
            int count,
            String query,
            Integer channelType,
            Boolean online,
            Integer planId,
            Boolean hasLink,
            VideoScopeQuery scope);

    void linkAll(Integer planId, VideoScopeQuery scope);

    void cleanAll(Integer planId, VideoScopeQuery scope);

    List<Integer> getLinkedChannelIds(Integer planId);

    Integer recording(String app, String stream);
}
