package com.genersoft.iot.vmp.storager.dao.provider;

import com.genersoft.iot.vmp.authorization.video.VideoListScopeSql;

import java.util.Map;

public class CloudRecordProvider {

    private static final String BASE_SQL = """
            SELECT cr.*
              FROM wvp_cloud_record cr
              LEFT JOIN wvp_device_channel record_channel
                ON record_channel.id = cr.channel_id
             WHERE 1=1
            """;

    public String getList(Map<String, Object> parameters) {
        return buildList(parameters, true);
    }

    public String getListForInternalMaintenance(Map<String, Object> parameters) {
        return buildList(parameters, false);
    }

    public String queryRecordFilePathList(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                SELECT cr.file_path
                  FROM wvp_cloud_record cr
                  LEFT JOIN wvp_device_channel record_channel
                    ON record_channel.id = cr.channel_id
                 WHERE 1=1
                <if test='app != null'> AND cr.app = #{app}</if>
                <if test='stream != null'> AND cr.stream = #{stream}</if>
                <if test='startTimeStamp != null'>
                  AND cr.end_time &gt;= #{startTimeStamp}
                </if>
                <if test='endTimeStamp != null'>
                  AND cr.start_time &lt;= #{endTimeStamp}
                </if>
                <if test='callId != null'> AND cr.call_id = #{callId}</if>
                <if test='mediaServerItemList != null'>
                  AND cr.media_server_id IN
                  <foreach collection='mediaServerItemList' item='item'
                           open='(' separator=',' close=')'>
                    #{item.id}
                  </foreach>
                </if>
                """);
        VideoListScopeSql.appendCloudRecordScope(
                sql, parameters, "cr", "record_channel");
        sql.append(" ORDER BY cr.start_time ASC ");
        return VideoListScopeSql.asScript(sql.toString());
    }

    public String countAuthorizedTaskSources(Map<String, Object> parameters) {
        StringBuilder sql = new StringBuilder("""
                SELECT COUNT(1)
                  FROM wvp_cloud_record cr
                  LEFT JOIN wvp_device_channel record_channel
                    ON record_channel.id = cr.channel_id
                 WHERE 1=1
                <if test='app != null'> AND cr.app = #{app}</if>
                <if test='stream != null'> AND cr.stream = #{stream}</if>
                <if test='callId != null'> AND cr.call_id = #{callId}</if>
                """);
        VideoListScopeSql.appendCloudRecordScope(
                sql, parameters, "cr", "record_channel");
        return VideoListScopeSql.asScript(sql.toString());
    }

    private String buildList(Map<String, Object> parameters, boolean scoped) {
        StringBuilder sql = new StringBuilder(BASE_SQL);
        sql.append("""
                <if test='query != null'>
                  AND (cr.app LIKE concat('%',#{query},'%') escape '/'
                       OR cr.stream LIKE concat('%',#{query},'%') escape '/')
                </if>
                <if test='app != null'> AND cr.app = #{app}</if>
                <if test='stream != null'> AND cr.stream = #{stream}</if>
                <if test='startTimeStamp != null'> AND cr.end_time &gt;= #{startTimeStamp}</if>
                <if test='endTimeStamp != null'> AND cr.start_time &lt;= #{endTimeStamp}</if>
                <if test='callId != null'> AND cr.call_id = #{callId}</if>
                <if test='mediaServerItemList != null'>
                  AND cr.media_server_id IN
                  <foreach collection='mediaServerItemList' item='item'
                           open='(' separator=',' close=')'>
                    #{item.id}
                  </foreach>
                </if>
                <if test='ids != null'>
                  AND cr.id IN
                  <foreach collection='ids' item='item' open='(' separator=',' close=')'>
                    #{item}
                  </foreach>
                </if>
                """);
        if (scoped) {
            VideoListScopeSql.appendCloudRecordScope(
                    sql, parameters, "cr", "record_channel");
        }
        sql.append("""
                <if test='ascOrder != null and ascOrder == true'>
                  ORDER BY cr.start_time ASC
                </if>
                <if test='ascOrder == null or ascOrder == false'>
                  ORDER BY cr.start_time DESC
                </if>
                """);
        return VideoListScopeSql.asScript(sql.toString());
    }
}
