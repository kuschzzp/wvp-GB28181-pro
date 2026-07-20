package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.storager.dao.dto.Role;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface RoleMapper {

    @Insert("""
            <script>
            insert into wvp_role
                (<if test="id > 0">id,</if>
                 name, role_key, role_sort, dept_data_scope, video_scope_type, status,
                 system_role, create_by, create_time, update_by, update_time)
            values
                (<if test="id > 0">#{id},</if>
                 #{name}, #{roleKey}, #{roleSort}, #{deptDataScope}, #{videoScopeType}, #{status},
                 #{systemRole}, #{createBy}, #{createTime}, #{updateBy}, #{updateTime})
            </script>
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(Role role);

    @Update("""
            update wvp_role
               set name = #{name},
                   role_key = #{roleKey},
                   role_sort = #{roleSort},
                   dept_data_scope = #{deptDataScope},
                   video_scope_type = #{videoScopeType},
                   status = #{status},
                   update_by = #{updateBy},
                   update_time = #{updateTime}
             where id = #{id}
            """)
    int update(Role role);

    @Update("""
            update wvp_role
               set status = #{status},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{id}
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") int status,
            @Param("operatorId") long operatorId,
            @Param("updateTime") java.time.LocalDateTime updateTime);

    @Delete("delete from wvp_role where id = #{id} and system_role = 0")
    int delete(long id);

    @Select("""
            select id, name, role_key as roleKey, role_sort as roleSort,
                   dept_data_scope as deptDataScope, video_scope_type as videoScopeType,
                   status, system_role as systemRole, create_by as createBy,
                   create_time as createTime, update_by as updateBy, update_time as updateTime
              from wvp_role
             where id = #{id}
            """)
    Role selectById(long id);

    @Select("""
            select id, name, role_key as roleKey, role_sort as roleSort,
                   dept_data_scope as deptDataScope, video_scope_type as videoScopeType,
                   status, system_role as systemRole, create_by as createBy,
                   create_time as createTime, update_by as updateBy, update_time as updateTime
              from wvp_role
             where id = #{id}
             for update
            """)
    Role selectByIdForUpdate(long id);

    @Select("""
            select id, name, role_key as roleKey, role_sort as roleSort,
                   dept_data_scope as deptDataScope, video_scope_type as videoScopeType,
                   status, system_role as systemRole, create_by as createBy,
                   create_time as createTime, update_by as updateBy, update_time as updateTime
              from wvp_role
             order by role_sort, id
            """)
    List<Role> selectAll();

    @Select("""
            <script>
            select id, name, role_key as roleKey, role_sort as roleSort,
                   dept_data_scope as deptDataScope, video_scope_type as videoScopeType,
                   status, system_role as systemRole
              from wvp_role
             where id in
             <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                 #{roleId}
             </foreach>
             order by id
            </script>
            """)
    List<Role> findByIds(@Param("roleIds") Collection<Long> roleIds);

    @Select("select count(*) from wvp_role where id = #{roleId} and system_role = 1")
    int countSystemRole(@Param("roleId") long roleId);
}
