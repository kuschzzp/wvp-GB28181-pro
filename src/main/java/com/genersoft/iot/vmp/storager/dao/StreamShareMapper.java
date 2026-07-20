package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.storager.dao.dto.StreamShare;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Mapper
@Repository
public interface StreamShareMapper {

    String SELECT_COLUMNS = """
            share_id as shareId, creator_user_id as creatorUserId,
            resource_type as resourceType, resource_id as resourceId,
            expires_at as expiresAt, max_views as maxViews,
            view_count as viewCount, passcode_hash as passcodeHash,
            status, authz_version as authzVersion,
            create_time as createTime, update_time as updateTime
            """;

    @Insert("""
            insert into wvp_stream_share
                (share_id, creator_user_id, resource_type, resource_id,
                 expires_at, max_views, view_count, passcode_hash, status,
                 authz_version, create_time, update_time)
            values
                (#{shareId}, #{creatorUserId}, #{resourceType}, #{resourceId},
                 #{expiresAt}, #{maxViews}, #{viewCount}, #{passcodeHash},
                 #{status}, #{authzVersion}, #{createTime}, #{updateTime})
            """)
    int insert(StreamShare share);

    @Select("select " + SELECT_COLUMNS + " from wvp_stream_share where share_id = #{shareId}")
    StreamShare selectById(@Param("shareId") String shareId);

    @Update("""
            update wvp_stream_share
               set view_count = view_count + 1, update_time = #{now}
             where share_id = #{shareId}
               and status = 'ACTIVE'
               and expires_at > #{now}
               and (max_views is null or view_count < max_views)
            """)
    int consume(@Param("shareId") String shareId, @Param("now") Instant now);

    @Update("""
            update wvp_stream_share
               set status = 'REVOKED', update_time = #{now}
             where share_id = #{shareId}
               and creator_user_id = #{creatorUserId}
               and status = 'ACTIVE'
            """)
    int revokeByCreator(
            @Param("shareId") String shareId,
            @Param("creatorUserId") long creatorUserId,
            @Param("now") Instant now);

    @Update("""
            update wvp_stream_share
               set status = 'REVOKED', update_time = #{now}
             where share_id = #{shareId} and status = 'ACTIVE'
            """)
    int revokeAny(@Param("shareId") String shareId, @Param("now") Instant now);
}
