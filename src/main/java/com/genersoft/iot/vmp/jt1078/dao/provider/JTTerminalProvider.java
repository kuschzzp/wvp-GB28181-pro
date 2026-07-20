package com.genersoft.iot.vmp.jt1078.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;

import java.util.Map;

public class JTTerminalProvider {

    private static final String BASE_SQL = "SELECT jt.* FROM wvp_jt_terminal jt WHERE 1=1 ";

    public String getDeviceList(Map<String, Object> parameters) {
        return buildDeviceList(parameters, true);
    }

    public String getDeviceListForInternalMaintenance(Map<String, Object> parameters) {
        return buildDeviceList(parameters, false);
    }

    private String buildDeviceList(Map<String, Object> parameters, boolean scoped) {
        StringBuilder sql = new StringBuilder(BASE_SQL);
        if (parameters.get("query") != null) {
            sql.append("""
                     AND (
                            jt.phone_number LIKE concat('%',#{query},'%')
                         OR jt.terminal_id LIKE concat('%',#{query},'%')
                         OR jt.province_id LIKE concat('%',#{query},'%')
                         OR jt.city_id LIKE concat('%',#{query},'%')
                         OR jt.maker_id LIKE concat('%',#{query},'%')
                         OR jt.model LIKE concat('%',#{query},'%')
                         OR jt.plate_no LIKE concat('%',#{query},'%')
                     )
                    """);
        }
        if (Boolean.TRUE.equals(parameters.get("online"))) {
            sql.append(" AND jt.status = true ");
        } else if (Boolean.FALSE.equals(parameters.get("online"))) {
            sql.append(" AND jt.status = false ");
        }
        if (scoped) {
            VideoListScopeSql.appendJtTerminalScope(sql, parameters, "jt");
        }
        sql.append(" ORDER BY jt.create_time ");
        return VideoListScopeSql.asScript(sql.toString());
    }
}
