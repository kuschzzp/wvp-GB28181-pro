package com.genersoft.iot.vmp.gb28181.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;

import java.util.Map;

public class MobilePositionProvider {

    public String queryPositionByDeviceIdAndTime(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder(baseSql());
        sql.append("""
                <if test='channelId != null'> AND mp.channel_id = #{channelId}</if>
                <if test='startTime != null'> AND mp.timestamp &gt;= #{startTime}</if>
                <if test='endTime != null'> AND mp.timestamp &lt;= #{endTime}</if>
                """);
        VideoListScopeSql.appendChannelScope(sql, parameters, "wdc");
        sql.append(" ORDER BY mp.timestamp ASC ");
        return VideoListScopeSql.asScript(sql.toString());
    }

    public String queryLatestPosition(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder(baseSql());
        sql.append("<if test='channelId != null'> AND mp.channel_id = #{channelId}</if>");
        VideoListScopeSql.appendChannelScope(sql, parameters, "wdc");
        sql.append(" ORDER BY mp.timestamp DESC LIMIT 1 ");
        return VideoListScopeSql.asScript(sql.toString());
    }

    private String baseSql() {
        return """
                SELECT mp.*
                  FROM wvp_mobile_position mp
                  JOIN wvp_device_channel wdc ON wdc.id = mp.channel_id
                 WHERE 1=1
                """;
    }
}
