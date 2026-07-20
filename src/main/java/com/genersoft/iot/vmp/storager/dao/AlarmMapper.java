package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.service.bean.Alarm;
import com.genersoft.iot.vmp.service.bean.AlarmType;
import com.genersoft.iot.vmp.storager.dao.provider.AlarmProvider;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface AlarmMapper {

    @SelectProvider(type = AlarmProvider.class, method = "getAlarms")
    List<Alarm> getAlarms(@Param("alarmType") List<AlarmType> alarmType,
                          @Param("beginTimeLong") Long beginTimeLong,
                          @Param("endTimeLong") Long endTimeLong,
                          @Param("scope") VideoScopeQuery scope);

    @SelectProvider(
            type = AlarmProvider.class,
            method = "getAlarmsForInternalMaintenance")
    List<Alarm> getAlarmsForInternalMaintenance(
            @Param("alarmType") List<AlarmType> alarmType,
            @Param("beginTimeLong") Long beginTimeLong,
            @Param("endTimeLong") Long endTimeLong);

    @Delete("<script>" +
            "DELETE FROM wvp_alarm WHERE id IN " +
            "<foreach collection='ids' item='id' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach>" +
            "</script>")
    int deleteAlarms(@Param("ids") List<Long> ids);

    @Insert("<script>" +
            "INSERT INTO wvp_alarm (channel_id, description, snap_path, record_path, longitude, latitude, alarm_type, alarm_time)" +
            " VALUES " +
            "<foreach collection='handlerCatchDataList' item='item' separator=','>" +
            "(#{item.channelId}, #{item.description}, #{item.snapPath}, #{item.recordPath}, #{item.longitude}, #{item.latitude}, #{item.alarmType}, #{item.alarmTime})" +
            "</foreach>" +
            "</script>")
    void insertAlarms(@Param("handlerCatchDataList") List<Alarm> handlerCatchDataList);

    @Select("SELECT snap_path FROM wvp_alarm WHERE id = #{id}")
    String getSnapPathById(@Param("id") Long id);

    @Delete("<script>" +
            "DELETE FROM wvp_alarm WHERE 1=1" +
            "<if test='alarmType != null and alarmType.size() > 0'>" +
            " AND alarm_type IN " +
            "<foreach collection='alarmType' item='item' open='(' separator=',' close=')'>" +
            "#{item}" +
            "</foreach>" +
            "</if>" +
            "<if test='beginTimeLong != null'> AND alarm_time &gt;= #{beginTimeLong}</if>" +
            "<if test='endTimeLong != null'> AND alarm_time &lt;= #{endTimeLong}</if>" +
            "</script>")
    int deleteAlarmsByCondition(@Param("alarmType") List<AlarmType> alarmType,
                                @Param("beginTimeLong") Long beginTimeLong,
                                @Param("endTimeLong") Long endTimeLong);
}
