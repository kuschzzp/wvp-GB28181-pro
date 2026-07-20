package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;
import com.genersoft.iot.vmp.media.bean.MediaServer;
import com.genersoft.iot.vmp.service.bean.CloudRecordItem;
import com.genersoft.iot.vmp.storager.dao.provider.CloudRecordProvider;
import org.apache.ibatis.annotations.*;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CloudRecordServiceMapper {

    @Insert(" <script>" +
            "INSERT INTO wvp_cloud_record (" +
            " app," +
            " stream," +
            "<if test=\"callId != null\"> call_id,</if>" +
            " start_time," +
            " end_time," +
            " media_server_id," +
            " file_name," +
            " folder," +
            " file_path," +
            " file_size," +
            " server_id," +
            " source_type," +
            " source_id," +
            " channel_id," +
            " time_len ) " +
            "VALUES (" +
            " #{app}," +
            " #{stream}," +
            " <if test=\"callId != null\"> #{callId},</if>" +
            " #{startTime}," +
            " #{endTime}," +
            " #{mediaServerId}," +
            " #{fileName}," +
            " #{folder}," +
            " #{filePath}," +
            " #{fileSize}," +
            " #{serverId}," +
            " #{sourceType}," +
            " #{sourceId}," +
            " #{channelId}," +
            " #{timeLen})" +
            " </script>")
    int add(CloudRecordItem cloudRecordItem);

    @SelectProvider(type = CloudRecordProvider.class, method = "getList")
    List<CloudRecordItem> getList(
            @Param("query") String query,
            @Param("app") String app,
            @Param("stream") String stream,
            @Param("startTimeStamp") Long startTimeStamp,
            @Param("endTimeStamp") Long endTimeStamp,
            @Param("callId") String callId,
            @Param("mediaServerItemList") List<MediaServer> mediaServerItemList,
            @Param("ids") List<Integer> ids,
            @Param("ascOrder") Boolean ascOrder,
            @Param("scope") VideoScopeQuery scope);

    @SelectProvider(
            type = CloudRecordProvider.class,
            method = "getListForInternalMaintenance")
    List<CloudRecordItem> getListForInternalMaintenance(
            @Param("query") String query,
            @Param("app") String app,
            @Param("stream") String stream,
            @Param("startTimeStamp") Long startTimeStamp,
            @Param("endTimeStamp") Long endTimeStamp,
            @Param("callId") String callId,
            @Param("mediaServerItemList") List<MediaServer> mediaServerItemList,
            @Param("ids") List<Integer> ids,
            @Param("ascOrder") Boolean ascOrder);


    @SelectProvider(type = CloudRecordProvider.class, method = "queryRecordFilePathList")
    List<String> queryRecordFilePathList(
            @Param("app") String app,
            @Param("stream") String stream,
            @Param("startTimeStamp") Long startTimeStamp,
            @Param("endTimeStamp") Long endTimeStamp,
            @Param("callId") String callId,
            @Param("mediaServerItemList") List<MediaServer> mediaServerItemList,
            @Param("scope") VideoScopeQuery scope);

    @SelectProvider(
            type = CloudRecordProvider.class,
            method = "countAuthorizedTaskSources")
    int countAuthorizedTaskSources(
            @Param("app") String app,
            @Param("stream") String stream,
            @Param("callId") String callId,
            @Param("scope") VideoScopeQuery scope);

    @Update(" <script>" +
            "update wvp_cloud_record set collect = #{collect} where file_path in " +
            " <foreach collection='cloudRecordItemList'  item='item'  open='(' separator=',' close=')' > #{item.filePath}</foreach>" +
            " </script>")
    int updateCollectList(@Param("collect") boolean collect, List<CloudRecordItem> cloudRecordItemList);

    @Delete(" <script>" +
            "delete from wvp_cloud_record where media_server_id=#{mediaServerId} and file_path in " +
            " <foreach collection='filePathList'  item='item'  open='(' separator=',' close=')' > #{item}</foreach>" +
            " </script>")
    void deleteByFileList(List<String> filePathList, @Param("mediaServerId") String mediaServerId);


    @Select(" <script>" +
            "select *" +
            " from wvp_cloud_record " +
            " where collect = false and end_time &lt;= #{endTimeStamp} and media_server_id  = #{mediaServerId} " +
            " </script>")
    List<CloudRecordItem> queryRecordListForDelete(@Param("endTimeStamp")Long endTimeStamp, String mediaServerId);

    @Update(" <script>" +
            "update wvp_cloud_record set collect = #{collect} where id = #{recordId} " +
            " </script>")
    int changeCollectById(@Param("collect") boolean collect, @Param("recordId") Integer recordId);

    @Delete(" <script>" +
            "delete from wvp_cloud_record where id in " +
            " <foreach collection='cloudRecordItemIdList'  item='item'  open='(' separator=',' close=')' > #{item.id}</foreach>" +
            " </script>")
    int deleteList(List<CloudRecordItem> cloudRecordItemIdList);

    @Select(" <script>" +
            "select *" +
            " from wvp_cloud_record " +
            "where call_id = #{callId}" +
            " </script>")
    List<CloudRecordItem> getListByCallId(@Param("callId") String callId);

    @Select(" <script>" +
            "select *" +
            " from wvp_cloud_record " +
            "where id = #{id}" +
            " </script>")
    CloudRecordItem queryOne(@Param("id") Integer id);

    @Select(" <script>" +
            "select *" +
            " from wvp_cloud_record " +
            " where app=#{app} and stream=#{stream} and file_name = #{fileName} " +
            " </script>")
    CloudRecordItem getListByFileName(@Param("app") String app, @Param("stream") String stream, @Param("fileName") String fileName);

    @Update(" <script>" +
            "update wvp_cloud_record set time_len = #{time}, end_time = #{endTime}  where id = #{id} " +
            " </script>")
    void updateTimeLen(@Param("id") int id, @Param("time") Long time, @Param("endTime") long endTime);

    @Select(" <script>" +
            "select media_server_id " +
            " from wvp_cloud_record " +
            " where 0 = 0" +
            " <if test= 'app != null '> and app=#{app}</if>" +
            " <if test= 'stream != null '> and stream=#{stream}</if>" +
            " <if test= 'startTimeStamp != null '> and end_time &gt;= #{startTimeStamp}</if>" +
            " <if test= 'endTimeStamp != null '> and start_time &lt;= #{endTimeStamp}</if>" +
            " group by media_server_id" +
            " </script>")
    List<String> queryMediaServerId(@Param("app") String app,
                   @Param("stream") String stream,
                   @Param("startTimeStamp")Long startTimeStamp,
                   @Param("endTimeStamp")Long endTimeStamp);

    @Select(" <script>" +
            "select * " +
            " from wvp_cloud_record where id in " +
            " <foreach collection='ids'  item='item'  open='(' separator=',' close=')' > #{item}</foreach>" +
            " </script>")
    List<CloudRecordItem> queryRecordByIds(Collection<Integer> ids);

    @Select(" <script>" +
            "select * " +
            " from wvp_cloud_record where 0=0 " +
            " <if test= 'app != null '> and app=#{app}</if>" +
            " <if test= 'stream != null '> and stream=#{stream}</if>" +
            " <if test= 'callId != null '> and call_id=#{callId}</if>" +
            " </script>")
    List<CloudRecordItem> queryRecordByAppStreamAndCallId(String app, String stream, String callId);

    @Select("""
            select id,
                   app,
                   stream,
                   call_id as callId,
                   source_type as sourceType,
                   source_id as sourceId,
                   channel_id as channelId,
                   source_reason as sourceReason
              from wvp_cloud_record
             where source_type = 'LEGACY_UNBOUND'
               and source_id is null
               and channel_id is null
               and source_reason is null
             order by id
             limit #{limit}
            """)
    List<CloudRecordItem> findLegacyUnbound(@Param("limit") int limit);

    @Select("""
            select id as recordId,
                   source_type as sourceType,
                   source_id as sourceId,
                   channel_id as channelId
              from wvp_cloud_record
             where call_id = #{callId}
               and source_type &lt;&gt; 'LEGACY_UNBOUND'
               and source_id is not null
               and channel_id is not null
            """)
    List<com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSource>
            findBoundSourcesByCallId(@Param("callId") String callId);

    @Select("""
            select id as recordId,
                   source_type as sourceType,
                   source_id as sourceId,
                   channel_id as channelId
              from wvp_cloud_record
             where app = #{app}
               and stream = #{stream}
               and source_type &lt;&gt; 'LEGACY_UNBOUND'
               and source_id is not null
               and channel_id is not null
            """)
    List<com.genersoft.iot.vmp.authorization.video.resolver.CloudRecordSource>
            findBoundSourcesByAppStream(
                    @Param("app") String app,
                    @Param("stream") String stream);

    @Update("""
            update wvp_cloud_record
               set source_type = #{sourceType},
                   source_id = #{sourceId},
                   channel_id = #{channelId},
                   source_reason = #{reasonCode}
             where id = #{recordId}
               and source_type = 'LEGACY_UNBOUND'
               and source_id is null
               and channel_id is null
               and source_reason is null
            """)
    int bindLegacyRecord(
            @Param("recordId") int recordId,
            @Param("sourceType") String sourceType,
            @Param("sourceId") long sourceId,
            @Param("channelId") long channelId,
            @Param("reasonCode") String reasonCode);

    @Update("""
            update wvp_cloud_record
               set source_type = 'LEGACY_UNBOUND',
                   source_id = null,
                   channel_id = null,
                   source_reason = #{reasonCode}
             where id = #{recordId}
               and source_type = 'LEGACY_UNBOUND'
               and source_id is null
               and channel_id is null
               and source_reason is null
            """)
    int markLegacyUnbound(
            @Param("recordId") int recordId,
            @Param("reasonCode") String reasonCode);
}
