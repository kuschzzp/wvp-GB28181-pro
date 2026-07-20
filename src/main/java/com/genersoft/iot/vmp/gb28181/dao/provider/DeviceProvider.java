package com.genersoft.iot.vmp.gb28181.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;

import java.util.Map;

public class DeviceProvider {

    public String getDeviceList(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                SELECT
                    coalesce(de.custom_name, de.name) as name,
                    de.id,
                    de.device_id,
                    de.manufacturer,
                    de.model,
                    de.firmware,
                    de.transport,
                    de.stream_mode,
                    de.on_line,
                    de.ip,
                    de.create_time,
                    de.update_time,
                    de.port,
                    de.expires,
                    de.subscribe_cycle_for_catalog,
                    de.subscribe_cycle_for_mobile_position,
                    de.mobile_position_submission_interval,
                    de.subscribe_cycle_for_alarm,
                    de.host_address,
                    de.charset,
                    de.ssrc_check,
                    de.geo_coord_sys,
                    de.media_server_id,
                    de.sdp_ip,
                    de.local_ip,
                    de.password,
                    de.as_message_channel,
                    de.heart_beat_interval,
                    de.heart_beat_count,
                    de.position_capability,
                    de.broadcast_push_after_ack,
                    de.server_id,
                    (select count(0)
                       from wvp_device_channel dc
                      where dc.data_type = #{dataType}
                        and dc.data_device_id = de.id) as channel_count
                  from wvp_device de
                 where 1=1
                """);
        if (parameters.get("status") != null) {
            sql.append(" AND de.on_line=#{status} ");
        }
        if (parameters.get("query") != null) {
            sql.append("""
                     AND (
                         coalesce(de.custom_name, de.name)
                             LIKE concat('%',#{query},'%') escape '/'
                         OR de.device_id LIKE concat('%',#{query},'%') escape '/'
                         OR de.ip LIKE concat('%',#{query},'%') escape '/'
                     )
                    """);
        }
        VideoListScopeSql.appendDeviceScope(sql, parameters, "de");
        sql.append(" order by de.create_time desc, de.device_id ");
        return VideoListScopeSql.asScript(sql.toString());
    }
}
