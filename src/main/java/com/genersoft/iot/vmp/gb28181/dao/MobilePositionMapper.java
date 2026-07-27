package com.genersoft.iot.vmp.gb28181.dao;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.gb28181.bean.MobilePosition;
import com.genersoft.iot.vmp.gb28181.dao.provider.MobilePositionProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;

import java.util.List;

@Mapper
public interface MobilePositionMapper {

    @Insert("INSERT INTO wvp_mobile_position (channel_id, timestamp, longitude, latitude, altitude, speed, direction, create_time)"+
            "VALUES (#{channelId}, #{timestamp}, #{longitude}, #{latitude}, #{altitude}, #{speed}, #{direction}, #{createTime})")
    int insertNewPosition(MobilePosition mobilePosition);

    @SelectProvider(
            type = MobilePositionProvider.class,
            method = "queryPositionByDeviceIdAndTime")
    List<MobilePosition> queryPositionByDeviceIdAndTime(
            @Param("channelId") Integer channelId,
            @Param("startTime") Long startTime,
            @Param("endTime") Long endTime,
            @Param("scope") VideoScopeQuery scope);

    @SelectProvider(type = MobilePositionProvider.class, method = "queryLatestPosition")
    MobilePosition queryLatestPosition(
            @Param("channelId") Integer channelId,
            @Param("scope") VideoScopeQuery scope);

    @Insert("<script> " +
            "<foreach collection='mobilePositions' index='index' item='item' separator=';'> " +
            "insert into wvp_mobile_position " +
            "(channel_id, timestamp,longitude,latitude,altitude,speed,direction," +
            "create_time)"+
            "values " +
            "( #{item.channelId}, #{item.timestamp}, #{item.longitude}, " +
            " #{item.latitude}, #{item.altitude}, #{item.speed},#{item.direction}," +
            " #{item.createTime}) " +
            "</foreach> " +
            "</script>")
    void batchAdd(List<MobilePosition> mobilePositions);
}
