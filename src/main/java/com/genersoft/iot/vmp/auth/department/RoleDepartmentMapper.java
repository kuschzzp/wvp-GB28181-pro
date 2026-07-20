package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.model.RoleDepartmentGrant;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface RoleDepartmentMapper {

    @Select("select dept_id from wvp_role_dept where role_id = #{roleId} order by dept_id")
    List<Long> findDepartmentIdsByRoleId(@Param("roleId") long roleId);

    @Delete("delete from wvp_role_dept where role_id = #{roleId}")
    int deleteByRoleId(@Param("roleId") long roleId);

    @Insert("""
            <script>
            insert into wvp_role_dept (role_id, dept_id) values
            <foreach collection="deptIds" item="deptId" separator=",">
                (#{roleId}, #{deptId})
            </foreach>
            </script>
            """)
    int insertBatch(
            @Param("roleId") long roleId,
            @Param("deptIds") Collection<Long> deptIds);

    @Select("""
            <script>
            select r.id as roleId, r.dept_data_scope as scopeType
              from wvp_role r
             where r.status = 0
               and r.id in
             <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                 #{roleId}
             </foreach>
             order by r.id
            </script>
            """)
    List<RoleDepartmentGrant> findEnabledScopes(@Param("roleIds") Collection<Long> roleIds);

    @Select("""
            <script>
            select distinct rd.dept_id
              from wvp_role_dept rd
              join wvp_role r on r.id = rd.role_id and r.status = 0
              join wvp_dept d on d.id = rd.dept_id and d.status = 0 and d.deleted = 0
             where rd.role_id in
             <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                 #{roleId}
             </foreach>
               and not exists (
                   select 1
                     from wvp_dept_closure path
                     join wvp_dept ancestor on ancestor.id = path.ancestor_id
                    where path.descendant_id = rd.dept_id
                      and (ancestor.status != 0 or ancestor.deleted != 0)
               )
             order by rd.dept_id
            </script>
            """)
    List<Long> findEnabledCustomDepartmentIds(@Param("roleIds") Collection<Long> roleIds);
}
