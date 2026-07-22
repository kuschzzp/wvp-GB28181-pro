package com.genersoft.iot.vmp.jt1078.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;

import java.util.Map;

public class JTChannelProvider {

    public final static String BASE_SQL = """
            SELECT jc.id as id,
                   jc.terminal_db_id as terminal_db_id,
                   jc.channel_id as channel_id,
                   jc.name as name,
                   jc.has_audio as has_audio,
                   jc.create_time as create_time,
                   jc.update_time as update_time,
                   wdc.id as gb_id,
                   wdc.gb_device_id as gb_device_id,
                   wdc.gb_name as gb_name,
                   wdc.gb_manufacturer as gb_manufacturer,
                   wdc.gb_model as gb_model,
                   wdc.gb_owner as gb_owner,
                   wdc.gb_civil_code as gb_civil_code,
                   wdc.gb_block as gb_block,
                   wdc.gb_address as gb_address,
                   wdc.gb_parental as gb_parental,
                   wdc.gb_parent_id as gb_parent_id,
                   wdc.gb_safety_way as gb_safety_way,
                   wdc.gb_register_way as gb_register_way,
                   wdc.gb_cert_num as gb_cert_num,
                   wdc.gb_certifiable as gb_certifiable,
                   wdc.gb_err_code as gb_err_code,
                   wdc.gb_end_time as gb_end_time,
                   wdc.gb_secrecy as gb_secrecy,
                   wdc.gb_ip_address as gb_ip_address,
                   wdc.gb_port as gb_port,
                   wdc.gb_password as gb_password,
                   wdc.gb_status as gb_status,
                   wdc.gb_longitude as gb_longitude,
                   wdc.gb_latitude as gb_latitude,
                   wdc.gps_altitude as gps_altitude,
                   wdc.gps_speed as gps_speed,
                   wdc.gps_direction as gps_direction,
                   wdc.gps_time as gps_time,
                   wdc.gb_business_group_id as gb_business_group_id,
                   wdc.gb_ptz_type as gb_ptz_type,
                   wdc.gb_position_type as gb_position_type,
                   wdc.gb_room_type as gb_room_type,
                   wdc.gb_use_type as gb_use_type,
                   wdc.gb_supply_light_type as gb_supply_light_type,
                   wdc.gb_direction_type as gb_direction_type,
                   wdc.gb_resolution as gb_resolution,
                   wdc.gb_download_speed as gb_download_speed,
                   wdc.gb_svc_space_support_mod as gb_svc_space_support_mod,
                   wdc.gb_svc_time_support_mode as gb_svc_time_support_mode,
                   coalesce(wdc.data_type, 200) as data_type,
                   coalesce(wdc.data_device_id, jc.id) as data_device_id,
                   wdc.stream_id as stream_id,
                   wdc.enable_broadcast as enable_broadcast,
                   wdc.map_level as map_level
              from wvp_jt_channel jc
              LEFT join wvp_device_channel wdc
                on jc.id = wdc.data_device_id and wdc.data_type = 200
            """;

    public String selectChannelByChannelId(Map<String, Object> params ){
        StringBuilder sqlBuild = new StringBuilder();
        sqlBuild.append(BASE_SQL);
        sqlBuild.append(" WHERE jc.terminal_db_id = #{terminalDbId} and jc.channel_id = #{channelId} ");
        return sqlBuild.toString();
    }
    public String selectChannelById(Map<String, Object> params ){
        StringBuilder sqlBuild = new StringBuilder();
        sqlBuild.append(BASE_SQL);
        sqlBuild.append(" WHERE jc.id = #{id}");
        return sqlBuild.toString();
    }
    public String selectAll(Map<String, Object> params ){
        return buildSelectAll(params, true);
    }

    public String selectAllForInternalMaintenance(Map<String, Object> params) {
        return buildSelectAll(params, false);
    }

    private String buildSelectAll(Map<String, Object> params, boolean scoped) {
        StringBuilder sqlBuild = new StringBuilder();
        sqlBuild.append(BASE_SQL);
        sqlBuild.append(" WHERE jc.terminal_db_id = #{terminalDbId} ");
        if (params.get("query") != null) {
            sqlBuild.append(" AND jc.name LIKE concat('%',#{query},'%')");
        }
        if (scoped) {
            VideoListScopeSql.appendJtChannelScope(sqlBuild, params, "jc");
        }
        sqlBuild.append(" ORDER BY jc.channel_id ");
        return VideoListScopeSql.asScript(sqlBuild.toString());
    }
}
