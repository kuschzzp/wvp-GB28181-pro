package com.genersoft.iot.vmp.streamPush.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.util.Map;

public class StreamPushProvider {

    public String selectAll(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder(baseSelectSql());
        sql.append(" WHERE 1=1 ");
        appendFilters(sql, parameters);
        VideoListScopeSql.appendSourceScope(
                sql, parameters, VideoResourceType.STREAM_PUSH, "st", "wdc");
        sql.append(" order by st.pushing desc, st.create_time desc ");
        return VideoListScopeSql.asScript(sql.toString());
    }

    public String selectAllForInternalMaintenance(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder(baseSelectSql());
        sql.append(" WHERE 1=1 ");
        appendFilters(sql, parameters);
        sql.append(" order by st.pushing desc, st.create_time desc ");
        return sql.toString();
    }

    private String baseSelectSql() {
        return """
                SELECT st.*,
                       st.id as data_device_id,
                       wdc.*,
                       wdc.id as gb_id
                  from wvp_stream_push st
                  left join wvp_device_channel wdc
                    on wdc.data_type = 2
                   and st.id = wdc.data_device_id
                """;
    }

    private void appendFilters(StringBuilder sql, Map<String, Object> parameters) {
        if (parameters.get("query") != null) {
            sql.append("""
                     AND (st.app LIKE concat('%',#{query},'%') escape '/'
                          OR st.stream LIKE concat('%',#{query},'%') escape '/'
                          OR wdc.gb_device_id LIKE concat('%',#{query},'%') escape '/'
                          OR wdc.gb_name LIKE concat('%',#{query},'%') escape '/')
                    """);
        }
        Object pushing = parameters.get("pushing");
        if (Boolean.TRUE.equals(pushing)) {
            sql.append(" AND st.pushing=true ");
        } else if (Boolean.FALSE.equals(pushing)) {
            sql.append(" AND st.pushing=false ");
        }
        if (parameters.get("mediaServerId") != null) {
            sql.append(" AND st.media_server_id=#{mediaServerId} ");
        }
    }
}
