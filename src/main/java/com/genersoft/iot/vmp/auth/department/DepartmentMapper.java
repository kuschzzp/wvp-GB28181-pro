package com.genersoft.iot.vmp.auth.department;

import com.genersoft.iot.vmp.auth.department.dto.DepartmentTreeRow;
import com.genersoft.iot.vmp.auth.department.model.Department;
import com.genersoft.iot.vmp.auth.department.model.DepartmentClosurePath;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.department.sql.DepartmentTreeSqlProvider;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
@Repository
public interface DepartmentMapper {

    @SelectProvider(type = DepartmentTreeSqlProvider.class, method = "query")
    List<DepartmentTreeRow> findVisibleTreeRows(@Param("scope") DepartmentScope scope);

    String DEPARTMENT_COLUMNS = """
            id, parent_id as parentId, dept_name as deptName, dept_code as deptCode,
            order_num as orderNum, leader_user_id as leaderUserId, status, deleted,
            create_by as createBy, create_time as createTime,
            update_by as updateBy, update_time as updateTime
            """;

    @Insert("""
            insert into wvp_dept
                (parent_id, dept_name, dept_code, order_num, leader_user_id, status, deleted,
                 create_by, create_time, update_by, update_time)
            values
                (#{parentId}, #{deptName}, #{deptCode}, #{orderNum}, #{leaderUserId},
                 #{status}, #{deleted}, #{createBy}, #{createTime}, #{updateBy}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id", keyColumn = "id")
    int insert(Department department);

    @Select("select " + DEPARTMENT_COLUMNS + " from wvp_dept where id = #{deptId}")
    Department findById(@Param("deptId") long deptId);

    @Select("select " + DEPARTMENT_COLUMNS + " from wvp_dept where id = #{deptId} for update")
    Department findByIdForUpdate(@Param("deptId") long deptId);

    @Select("select count(*) from wvp_dept where dept_code = #{deptCode}")
    int countByCode(@Param("deptCode") String deptCode);

    @Select("select count(*) from wvp_dept where dept_code = #{deptCode} and id <> #{deptId}")
    int countByCodeExcludingId(
            @Param("deptCode") String deptCode,
            @Param("deptId") long deptId);

    @Select("""
            select count(*)
              from wvp_dept d
             where d.id = #{deptId}
               and d.status = 0
               and d.deleted = 0
               and not exists (
                   select 1
                     from wvp_dept_closure path
                     join wvp_dept ancestor on ancestor.id = path.ancestor_id
                    where path.descendant_id = d.id
                      and (ancestor.status != 0 or ancestor.deleted != 0)
               )
            """)
    int countEnabled(@Param("deptId") long deptId);

    @Select("""
            <script>
            select count(*)
              from wvp_dept d
             where d.status = 0
               and d.deleted = 0
               and d.id in
             <foreach collection="deptIds" item="deptId" open="(" separator="," close=")">
                 #{deptId}
             </foreach>
               and not exists (
                   select 1
                     from wvp_dept_closure path
                     join wvp_dept ancestor on ancestor.id = path.ancestor_id
                    where path.descendant_id = d.id
                      and (ancestor.status != 0 or ancestor.deleted != 0)
               )
            </script>
            """)
    int countEnabledByIds(@Param("deptIds") java.util.Collection<Long> deptIds);

    @Select("""
            select ancestor_id as ancestorId, descendant_id as descendantId, depth
              from wvp_dept_closure
             where descendant_id = #{deptId}
             order by depth desc, ancestor_id
             for update
            """)
    List<DepartmentClosurePath> findAncestorPathsForUpdate(@Param("deptId") long deptId);

    @Select("""
            select ancestor_id as ancestorId, descendant_id as descendantId, depth
              from wvp_dept_closure
             where ancestor_id = #{deptId}
             order by depth, descendant_id
             for update
            """)
    List<DepartmentClosurePath> findSubtreePathsForUpdate(@Param("deptId") long deptId);

    @Insert("""
            insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
            select ancestor_id, #{newDeptId}, depth + 1
              from wvp_dept_closure
             where descendant_id = #{parentId}
            """)
    int insertAncestorPaths(
            @Param("parentId") long parentId,
            @Param("newDeptId") long newDeptId);

    @Insert("""
            insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
            values (#{deptId}, #{deptId}, 0)
            """)
    int insertSelfPath(@Param("deptId") long deptId);

    @Insert("""
            insert into wvp_dept_closure (ancestor_id, descendant_id, depth)
            values (#{ancestorId}, #{descendantId}, #{depth})
            """)
    int insertPath(
            @Param("ancestorId") long ancestorId,
            @Param("descendantId") long descendantId,
            @Param("depth") int depth);

    @Delete("""
            delete from wvp_dept_closure
             where ancestor_id = #{ancestorId}
               and descendant_id = #{descendantId}
            """)
    int deletePath(
            @Param("ancestorId") long ancestorId,
            @Param("descendantId") long descendantId);

    @Update("""
            update wvp_dept
               set parent_id = #{parentId},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{deptId}
               and deleted = 0
            """)
    int updateParent(
            @Param("deptId") long deptId,
            @Param("parentId") long parentId,
            @Param("operatorId") long operatorId,
            @Param("updateTime") LocalDateTime updateTime);

    @Update("""
            update wvp_dept
               set dept_name = #{deptName},
                   dept_code = #{deptCode},
                   order_num = #{orderNum},
                   leader_user_id = #{leaderUserId},
                   status = #{status},
                   update_by = #{operatorId},
                   update_time = #{updateTime}
             where id = #{deptId}
               and deleted = 0
            """)
    int updateDetails(
            @Param("deptId") long deptId,
            @Param("deptName") String deptName,
            @Param("deptCode") String deptCode,
            @Param("orderNum") int orderNum,
            @Param("leaderUserId") Long leaderUserId,
            @Param("status") int status,
            @Param("operatorId") long operatorId,
            @Param("updateTime") LocalDateTime updateTime);

    @Select("select count(*) from wvp_dept where parent_id = #{deptId}")
    int countDirectChildren(@Param("deptId") long deptId);

    @Select("select count(*) from wvp_user where dept_id = #{deptId}")
    int countUsers(@Param("deptId") long deptId);

    @Delete("delete from wvp_dept where id = #{deptId}")
    int deleteById(@Param("deptId") long deptId);

    @Select("""
            select d.id
              from wvp_dept_closure c
              join wvp_dept d on d.id = c.descendant_id
             where c.ancestor_id = #{deptId}
               and d.status = 0
               and d.deleted = 0
               and not exists (
                   select 1
                     from wvp_dept_closure path
                     join wvp_dept path_dept on path_dept.id = path.ancestor_id
                    where path.descendant_id = d.id
                      and path.depth <= c.depth
                      and (path_dept.status != 0 or path_dept.deleted != 0)
               )
             order by c.depth, d.order_num, d.id
            """)
    List<Long> findEnabledDescendantIds(@Param("deptId") long deptId);
}
