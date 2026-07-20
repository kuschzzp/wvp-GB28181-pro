package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.auth.apikey.ApiKeyStatus;
import com.genersoft.iot.vmp.storager.dao.dto.UserApiKey;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface UserApiKeyMapper {

    String SELECT_COLUMNS = """
            uak.id, uak.user_id as userId, uak.app, uak.key_hash as keyHash,
            uak.key_prefix as keyPrefix, uak.key_last4 as keyLast4,
            uak.api_key as legacyApiKey, uak.expired_at as expiredAt, uak.remark,
            uak.status, uak.ip_allowlist as ipAllowlist,
            uak.authz_version as authzVersion, uak.last_used_at as lastUsedAt,
            uak.create_time as createTime, uak.update_time as updateTime, u.username
            """;

    @Insert("""
            insert into wvp_user_api_key
                (user_id, app, key_hash, key_prefix, key_last4, api_key, expired_at, remark,
                 status, ip_allowlist, authz_version, create_time, update_time)
            values
                (#{userId}, #{app}, #{keyHash}, #{keyPrefix}, #{keyLast4}, null, #{expiredAt}, #{remark},
                 #{status}, #{ipAllowlist}, #{authzVersion}, #{createTime}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(UserApiKey userApiKey);

    @Update("""
            update wvp_user_api_key
               set key_hash = #{keyHash},
                   key_prefix = #{keyPrefix},
                   key_last4 = #{keyLast4},
                   api_key = null,
                   expired_at = #{expiredAt},
                   status = #{status},
                   authz_version = #{authzVersion},
                   update_time = #{updateTime}
             where id = #{id} and user_id = #{userId}
            """)
    int rotate(UserApiKey userApiKey);

    @Update("""
            update wvp_user_api_key
               set status = #{status}, update_time = #{updateTime}
             where id = #{id} and user_id = #{ownerId}
            """)
    int updateStatus(
            @Param("ownerId") long ownerId,
            @Param("id") long id,
            @Param("status") ApiKeyStatus status,
            @Param("updateTime") String updateTime);

    @Update("""
            update wvp_user_api_key
               set remark = #{remark}, update_time = #{updateTime}
             where id = #{id} and user_id = #{ownerId}
            """)
    int updateRemark(
            @Param("ownerId") long ownerId,
            @Param("id") long id,
            @Param("remark") String remark,
            @Param("updateTime") String updateTime);

    @Update("update wvp_user_api_key set ip_allowlist = #{ipAllowlist} where id = #{id}")
    int updateIpAllowlist(
            @Param("id") long id,
            @Param("ipAllowlist") String ipAllowlist);

    @Delete("delete from wvp_user_api_key where id = #{id} and user_id = #{ownerId}")
    int delete(@Param("ownerId") long ownerId, @Param("id") long id);

    @Select("select " + SELECT_COLUMNS + " from wvp_user_api_key uak left join wvp_user u on u.id = uak.user_id where uak.id = #{id}")
    UserApiKey selectById(@Param("id") long id);

    @Select("select " + SELECT_COLUMNS + " from wvp_user_api_key uak left join wvp_user u on u.id = uak.user_id where uak.key_hash = #{keyHash}")
    UserApiKey selectByHash(@Param("keyHash") String keyHash);

    @Select("select " + SELECT_COLUMNS + " from wvp_user_api_key uak left join wvp_user u on u.id = uak.user_id where uak.status = 'LEGACY' and uak.api_key = #{rawKey}")
    UserApiKey selectLegacyByRawKey(@Param("rawKey") String rawKey);

    @Select("select " + SELECT_COLUMNS + " from wvp_user_api_key uak left join wvp_user u on u.id = uak.user_id where uak.user_id = #{ownerId} order by uak.id desc")
    List<UserApiKey> findByOwnerId(@Param("ownerId") long ownerId);

    @Update("update wvp_user_api_key set last_used_at = #{usedAt} where id = #{id}")
    int updateLastUsedAt(@Param("id") long id, @Param("usedAt") LocalDateTime usedAt);
}
