package com.genersoft.iot.vmp.storager.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;

import java.util.Map;

public class AlarmProvider {

    private static final String BASE_SQL = """
            SELECT wa.*,
                   coalesce(wdc.gb_device_id, wdc.device_id) as channelDeviceId,
                   coalesce(wdc.gb_name, wdc.name) as channelName
              FROM wvp_alarm wa
              LEFT JOIN wvp_device_channel wdc ON wdc.id = wa.channel_id
             WHERE 1=1
            """;

    public String getAlarms(Map<String, Object> parameters) {
        return buildQuery(parameters, true);
    }

    public String getAlarmsForInternalMaintenance(Map<String, Object> parameters) {
        return buildQuery(parameters, false);
    }

    private String buildQuery(Map<String, Object> parameters, boolean scoped) {
        StringBuilder sql = new StringBuilder(BASE_SQL);
        sql.append("""
                <if test='alarmType != null and alarmType.size() > 0'>
                  AND wa.alarm_type IN
                  <foreach collection='alarmType' item='item' open='(' separator=',' close=')'>
                    #{item}
                  </foreach>
                </if>
                <if test='beginTimeLong != null'>
                  AND wa.alarm_time &gt;= #{beginTimeLong}
                </if>
                <if test='endTimeLong != null'>
                  AND wa.alarm_time &lt;= #{endTimeLong}
                </if>
                """);
        if (scoped) {
            VideoListScopeSql.appendChannelScope(sql, parameters, "wdc");
        }
        sql.append(" ORDER BY wa.alarm_time DESC ");
        return VideoListScopeSql.asScript(sql.toString());
    }
}
