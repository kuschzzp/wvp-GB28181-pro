package com.genersoft.iot.vmp.authorization.video.resolver;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface VideoResourceLookupMapper {

    @Select("select id from wvp_device where id = #{id}")
    Long findGbDeviceId(@Param("id") long id);

    @Select("""
            select id
              from wvp_device
             where device_id = #{deviceId}
             order by id
            """)
    List<Long> findGbDeviceIdsByDeviceId(@Param("deviceId") String deviceId);

    @Select("select id from wvp_device_channel where id = #{id}")
    Long findChannelId(@Param("id") long id);

    @Select("""
            select id
              from wvp_device_channel
             where data_type = #{dataType}
               and data_device_id = #{sourceId}
            """)
    List<Long> findChannelIdsBySource(
            @Param("dataType") int dataType,
            @Param("sourceId") long sourceId);

    @Select("""
            select channel.data_type as dataType,
                   channel.data_device_id as dataDeviceId,
                   jt_channel.terminal_db_id as jtTerminalId
              from wvp_device_channel channel
              left join wvp_jt_channel jt_channel
                on channel.data_type = 200
               and jt_channel.id = channel.data_device_id
             where channel.id = #{channelId}
            """)
    ChannelAuthorizationSource findChannelAuthorizationSource(
            @Param("channelId") long channelId);

    @Select("""
            select terminal_db_id
              from wvp_jt_channel
             where id = #{jtChannelId}
            """)
    Long findJtTerminalIdByChannelId(@Param("jtChannelId") long jtChannelId);

    @Select("select id from wvp_jt_terminal where id = #{id}")
    Long findJtTerminalId(@Param("id") long id);

    @Select("select id from wvp_jt_channel where id = #{id}")
    Long findJtChannelId(@Param("id") long id);

    @Select("""
            select id
              from wvp_jt_terminal
             where phone_number = #{phoneNumber}
             order by id
            """)
    List<Long> findJtTerminalIdsByPhoneNumber(
            @Param("phoneNumber") String phoneNumber);

    @Select("""
            select channel.id
              from wvp_jt_channel channel
              join wvp_jt_terminal terminal
                on terminal.id = channel.terminal_db_id
             where terminal.phone_number = #{phoneNumber}
               and channel.channel_id = #{channelId}
             order by channel.id
            """)
    List<Long> findJtChannelIdsByPhoneNumberAndChannelId(
            @Param("phoneNumber") String phoneNumber,
            @Param("channelId") int channelId);

    @Select("select id from wvp_stream_proxy where id = #{id}")
    Long findStreamProxyId(@Param("id") long id);

    @Select("select id from wvp_stream_push where id = #{id}")
    Long findStreamPushId(@Param("id") long id);

    @Select("""
            select channel.id
              from wvp_device_channel channel
              join wvp_device device on device.id = channel.data_device_id
             where channel.data_type = 1
               and device.device_id = #{deviceId}
               and coalesce(channel.gb_device_id, channel.device_id) = #{channelId}
            """)
    List<Long> findGbChannelIds(
            @Param("deviceId") String deviceId,
            @Param("channelId") String channelId);

    @Select("""
            <script>
            select channel.id
              from wvp_device_channel channel
              left join wvp_device device
                on channel.data_type = 1
               and device.id = channel.data_device_id
             where coalesce(channel.gb_device_id, channel.device_id)
                   = #{channelDeviceId}
            <if test="gbDeviceId != null and gbDeviceId != ''">
               and channel.data_type = 1
               and device.device_id = #{gbDeviceId}
            </if>
             order by channel.id
            </script>
            """)
    List<Long> findUnifiedChannelIds(
            @Param("channelDeviceId") String channelDeviceId,
            @Param("gbDeviceId") String gbDeviceId);

    @Select("""
            select id
              from wvp_stream_proxy
             where app = #{app}
               and stream = #{stream}
            """)
    Long findStreamProxyIdByAppStream(
            @Param("app") String app,
            @Param("stream") String stream);

    @Select("""
            select id
              from wvp_stream_push
             where app = #{app}
               and stream = #{stream}
            """)
    Long findStreamPushIdByAppStream(
            @Param("app") String app,
            @Param("stream") String stream);

    @Select("""
            select id as recordId,
                   source_type as sourceType,
                   source_id as sourceId,
                   channel_id as channelId
              from wvp_cloud_record
             where id = #{recordId}
            """)
    CloudRecordSource findCloudRecordSource(@Param("recordId") long recordId);

    @Select("select channel_id from wvp_alarm where id = #{alarmId}")
    Long findAlarmChannelId(@Param("alarmId") long alarmId);
}
