package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.auth.user.dto.UserSummary;
import com.genersoft.iot.vmp.auth.user.sql.UserScopeSqlProvider;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface UserMapper {

    @SelectProvider(type = UserScopeSqlProvider.class, method = "query")
    List<UserSummary> queryScoped(
            @Param("query") UserQuery query,
            @Param("scope") DepartmentScope scope);

    @SelectProvider(type = UserScopeSqlProvider.class, method = "findVisible")
    UserSummary findVisibleScoped(
            @Param("userId") long userId,
            @Param("scope") DepartmentScope scope);

    @Update("""
            update wvp_user
               set nickname = #{nickname},
                   dept_id = #{deptId},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{userId}
               and deleted = 0
            """)
    int updateAdminProfile(
            @Param("userId") long userId,
            @Param("nickname") String nickname,
            @Param("deptId") long deptId,
            @Param("operatorId") long operatorId,
            @Param("updateTime") String updateTime);

    @Update("""
            update wvp_user
               set status = #{status},
                   locked = #{locked},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{userId}
               and deleted = 0
            """)
    int updateAccountStatus(
            @Param("userId") long userId,
            @Param("status") int status,
            @Param("locked") boolean locked,
            @Param("operatorId") long operatorId,
            @Param("updateTime") String updateTime);

    @Update("""
            update wvp_user
               set nickname = #{nickname},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{userId}
               and deleted = 0
            """)
    int updateOwnProfile(
            @Param("userId") long userId,
            @Param("nickname") String nickname,
            @Param("operatorId") long operatorId,
            @Param("updateTime") String updateTime);

    @Insert("""
            insert into wvp_user
                (username, password, role_id, dept_id, nickname, password_version,
                 push_key, create_time, update_time, create_by, update_by)
            values
                (#{username}, #{password}, #{role.id}, #{deptId}, #{nickname}, #{passwordVersion},
                 #{pushKey}, #{createTime}, #{updateTime}, #{createBy}, #{updateBy})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(User user);

    @Results(id = "roleMap", value = {
            @Result(column = "create_time", property = "createTime"),
            @Result(column = "update_time", property = "updateTime"),
            @Result(column = "push_key", property = "pushKey"),
            @Result(column = "dept_id", property = "deptId"),
            @Result(column = "token_version", property = "tokenVersion"),
            @Result(column = "password_version", property = "passwordVersion"),
            @Result(column = "last_login_at", property = "lastLoginAt"),
            @Result(column = "create_by", property = "createBy"),
            @Result(column = "update_by", property = "updateBy"),
            @Result(column = "role_id", property = "role.id"),
            @Result(column = "role_name", property = "role.name"),
            @Result(column = "role_authority", property = "role.authority"),
            @Result(column = "role_create_time", property = "role.createTime"),
            @Result(column = "role_update_time", property = "role.updateTime")
    })
    @Select("select u.*, r.name as role_name, r.role_key as role_authority, r.create_time as role_create_time, r.update_time as role_update_time from wvp_user u left join wvp_role r on u.role_id=r.id where u.id=#{id}")
    User selectById(long id);

    @Select("select u.*, r.name as role_name, r.role_key as role_authority, r.create_time as role_create_time, r.update_time as role_update_time from wvp_user u left join wvp_role r on u.role_id=r.id where u.username=#{username}")
    @ResultMap(value="roleMap")
    User getUserByUsername(String username);

    @Select("select u.*, r.name as role_name, r.role_key as role_authority, r.create_time as role_create_time, r.update_time as role_update_time from wvp_user u left join wvp_role r on u.role_id=r.id where u.deleted = 0")
    @ResultMap(value="roleMap")
    List<User> selectAll();

    @Select("select u.*, r.name as role_name, r.role_key as role_authority, r.create_time as role_create_time, r.update_time as role_update_time from wvp_user u left join wvp_role r on u.role_id=r.id where u.deleted = 0")
    @ResultMap(value="roleMap")
    List<User> getUsers();

    @Update("UPDATE wvp_user set push_key=#{pushKey} where id=#{id}")
    int changePushKey(@Param("id") long id, @Param("pushKey") String pushKey);

    @Update("""
            update wvp_user
               set deleted = 1,
                   token_version = token_version + 1,
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{userId}
               and deleted = 0
            """)
    int softDelete(
            @Param("userId") long userId,
            @Param("operatorId") long operatorId,
            @Param("updateTime") String updateTime);

    @Update("""
            update wvp_user
               set password = #{newPassword},
                   password_version = #{passwordVersion},
                   token_version = token_version + 1,
                   update_time = #{updateTime}
             where id = #{userId}
               and password = #{expectedPassword}
               and deleted = 0
            """)
    int upgradePassword(
            @Param("userId") long userId,
            @Param("expectedPassword") String expectedPassword,
            @Param("newPassword") String newPassword,
            @Param("passwordVersion") int passwordVersion,
            @Param("updateTime") String updateTime);

    @Update("update wvp_user set last_login_at = #{lastLoginAt} where id = #{userId} and deleted = 0")
    int updateLastLogin(
            @Param("userId") long userId,
            @Param("lastLoginAt") java.time.LocalDateTime lastLoginAt);

    @Select("""
            select token_version
              from wvp_user
             where id = #{userId}
               and status = 0
               and locked = 0
               and deleted = 0
            """)
    Long findActiveTokenVersion(@Param("userId") long userId);

    @Update("""
            <script>
            update wvp_user
               set token_version = token_version + 1,
                   update_time = #{updateTime}
             where id in
             <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                 #{userId}
             </foreach>
               and deleted = 0
            </script>
            """)
    int bumpTokenVersions(
            @Param("userIds") java.util.Collection<Long> userIds,
            @Param("updateTime") String updateTime);

    @Select("""
            select distinct ur.user_id
              from wvp_user_role_rel ur
              join wvp_user u on u.id = ur.user_id
             where ur.role_id = #{roleId}
               and u.deleted = 0
             order by ur.user_id
            """)
    List<Long> findUserIdsByRoleId(@Param("roleId") long roleId);

    @Select("""
            select distinct ur.user_id
              from wvp_user_role_rel ur
             where ur.role_id = #{roleId}
             order by ur.user_id
            """)
    List<Long> findAllUserIdsByRoleId(@Param("roleId") long roleId);
}
