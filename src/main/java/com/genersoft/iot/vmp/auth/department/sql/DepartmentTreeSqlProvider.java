package com.genersoft.iot.vmp.auth.department.sql;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;

import java.util.Map;

public class DepartmentTreeSqlProvider {

    public String query(Map<String, Object> parameters) {
        Object value = parameters == null ? null : parameters.get("scope");
        if (!(value instanceof DepartmentScope scope)) {
            throw new IllegalArgumentException("缺少参数: scope");
        }
        StringBuilder sql = new StringBuilder("""
                <script>
                select d.id,
                       d.parent_id as parentId,
                       d.dept_name as deptName,
                       d.dept_code as deptCode,
                       d.order_num as orderNum,
                       d.leader_user_id as leaderUserId,
                       d.status,
                """);
        appendReadOnlyExpression(sql, scope);
        sql.append("""
                       as readOnlyPath
                  from wvp_dept d
                 where d.status = 0
                   and d.deleted = 0
                   and not exists (
                       select 1
                         from wvp_dept_closure active_path
                         join wvp_dept active_ancestor
                           on active_ancestor.id = active_path.ancestor_id
                        where active_path.descendant_id = d.id
                          and (active_ancestor.status != 0 or active_ancestor.deleted != 0)
                   )
                """);
        appendScopeFilter(sql, scope);
        sql.append(" order by d.order_num, d.id\n</script>");
        return sql.toString();
    }

    private void appendReadOnlyExpression(StringBuilder sql, DepartmentScope scope) {
        if (scope.all()) {
            sql.append(" 0");
            return;
        }
        if (scope.isNone()) {
            sql.append(" 1");
            return;
        }
        sql.append(" case when (");
        appendDirectTarget(sql, scope);
        sql.append(") then 0 else 1 end");
    }

    private void appendScopeFilter(StringBuilder sql, DepartmentScope scope) {
        if (scope.all()) {
            return;
        }
        if (scope.isNone()) {
            sql.append(" and 1 = 0\n");
            return;
        }
        sql.append("""
                   and exists (
                       select 1
                         from wvp_dept_closure visible_path
                        where visible_path.ancestor_id = d.id
                          and (
                """);
        appendTargetByDescendant(sql, scope);
        sql.append("""
                          )
                          and not exists (
                              select 1
                                from wvp_dept_closure target_path
                                join wvp_dept target_ancestor
                                  on target_ancestor.id = target_path.ancestor_id
                               where target_path.descendant_id = visible_path.descendant_id
                                 and (target_ancestor.status != 0
                                      or target_ancestor.deleted != 0)
                          )
                   )
                """);
    }

    private void appendDirectTarget(StringBuilder sql, DepartmentScope scope) {
        boolean hasDepartments = !scope.deptIds().isEmpty();
        if (hasDepartments) {
            sql.append(" d.id in ");
            appendDepartmentForeach(sql);
        }
        if (scope.includeSelf()) {
            if (hasDepartments) {
                sql.append(" or ");
            }
            sql.append("exists (select 1 from wvp_user scope_user "
                    + "where scope_user.id = #{scope.userId} "
                    + "and scope_user.dept_id = d.id and scope_user.deleted = 0)");
        }
    }

    private void appendTargetByDescendant(StringBuilder sql, DepartmentScope scope) {
        boolean hasDepartments = !scope.deptIds().isEmpty();
        if (hasDepartments) {
            sql.append(" visible_path.descendant_id in ");
            appendDepartmentForeach(sql);
        }
        if (scope.includeSelf()) {
            if (hasDepartments) {
                sql.append(" or ");
            }
            sql.append("exists (select 1 from wvp_user scope_user "
                    + "where scope_user.id = #{scope.userId} "
                    + "and scope_user.dept_id = visible_path.descendant_id "
                    + "and scope_user.deleted = 0)");
        }
    }

    private void appendDepartmentForeach(StringBuilder sql) {
        sql.append("""
                <foreach collection="scope.deptIds" item="deptId" open="(" separator="," close=")">
                    #{deptId}
                </foreach>
                """);
    }
}
