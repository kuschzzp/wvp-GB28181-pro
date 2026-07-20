package com.genersoft.iot.vmp.authorization.video.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;

import java.util.Map;

public class VideoOverviewProvider {

    public String deviceOverview(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                select count(distinct de.id) as total,
                       count(distinct case
                           when de.on_line = true then de.id
                       end) as online
                  from wvp_device de
                 where 1=1
                """);
        VideoListScopeSql.appendDeviceScope(sql, parameters, "de");
        return VideoListScopeSql.asScript(sql.toString());
    }

    public String channelOverview(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                select count(distinct dc.id) as total,
                       count(distinct case
                           when coalesce(dc.gb_status, dc.status) = 'ON'
                           then dc.id
                       end) as online
                  from wvp_device_channel dc
                 where 1=1
                """);
        VideoListScopeSql.appendChannelScope(sql, parameters, "dc");
        return VideoListScopeSql.asScript(sql.toString());
    }

    public String pushOverview(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                select count(distinct st.id) as total,
                       count(distinct case
                           when st.pushing = true then st.id
                       end) as online
                  from wvp_stream_push st
                  left join wvp_device_channel wdc
                    on wdc.data_type = 2
                   and wdc.data_device_id = st.id
                 where 1=1
                """);
        VideoListScopeSql.appendSourceScope(
                sql, parameters, VideoResourceType.STREAM_PUSH, "st", "wdc");
        return VideoListScopeSql.asScript(sql.toString());
    }

    public String proxyOverview(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                select count(distinct st.id) as total,
                       count(distinct case
                           when st.pulling = true then st.id
                       end) as online
                  from wvp_stream_proxy st
                  left join wvp_device_channel wdc
                    on wdc.data_type = 3
                   and wdc.data_device_id = st.id
                 where 1=1
                """);
        VideoListScopeSql.appendSourceScope(
                sql, parameters, VideoResourceType.STREAM_PROXY, "st", "wdc");
        return VideoListScopeSql.asScript(sql.toString());
    }
}
