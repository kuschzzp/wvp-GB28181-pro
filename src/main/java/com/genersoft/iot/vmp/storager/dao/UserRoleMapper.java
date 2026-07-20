package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.auth.user.dto.UserRoleRef;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface UserRoleMapper {

    @Select("""
            <script>
            select user_id as userId, role_id as roleId
              from wvp_user_role_rel
             where user_id in
             <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                 #{userId}
             </foreach>
             order by user_id, role_id
            </script>
            """)
    List<UserRoleRef> findRoleRefsByUserIds(@Param("userIds") Collection<Long> userIds);

    @Delete("delete from wvp_user_role_rel where user_id = #{userId}")
    int deleteByUserId(@Param("userId") long userId);

    @Insert("""
            <script>
            insert into wvp_user_role_rel (user_id, role_id) values
            <foreach collection="roleIds" item="roleId" separator=",">
                (#{userId}, #{roleId})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("userId") long userId, @Param("roleIds") Collection<Long> roleIds);

    @Select("select role_id from wvp_user_role_rel where user_id = #{userId} order by role_id")
    List<Long> findRoleIdsByUserId(@Param("userId") long userId);

    @Select("select min(role_id) from wvp_user_role_rel where user_id = #{userId}")
    Long findFirstRoleIdByUserId(@Param("userId") long userId);

    @Update("update wvp_user set role_id = #{roleId}, update_time = #{updateTime} where id = #{userId} and deleted = 0")
    int updateLegacyRoleId(
            @Param("userId") long userId,
            @Param("roleId") Long roleId,
            @Param("updateTime") String updateTime);
}
