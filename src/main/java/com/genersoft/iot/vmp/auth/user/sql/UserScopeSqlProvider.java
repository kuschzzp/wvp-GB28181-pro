package com.genersoft.iot.vmp.auth.user.sql;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;

import java.util.Map;
import java.util.Objects;

public class UserScopeSqlProvider {

    public String query(Map<String, Object> parameters) {
        UserQuery query = requireParameter(parameters, "query", UserQuery.class);
        DepartmentScope scope = requireParameter(parameters, "scope", DepartmentScope.class);
        return buildSelect(query, scope, false);
    }

    public String findVisible(Map<String, Object> parameters) {
        DepartmentScope scope = requireParameter(parameters, "scope", DepartmentScope.class);
        return buildSelect(null, scope, true);
    }

    private String buildSelect(UserQuery query, DepartmentScope scope, boolean byUserId) {
        StringBuilder sql = new StringBuilder("""
                <script>
                select u.id,
                       u.username,
                       u.nickname,
                       u.dept_id as deptId,
                       d.dept_name as deptName,
                       u.status,
                       u.locked,
                       u.last_login_at as lastLoginAt,
                       case when exists (
                           select 1
                             from wvp_user_role_rel system_user_role
                             join wvp_role system_role on system_role.id = system_user_role.role_id
                            where system_user_role.user_id = u.id
                              and system_role.system_role = 1
                              and system_role.status = 0
                       ) then 1 else 0 end as systemUser
                  from wvp_user u
                  join wvp_dept d on d.id = u.dept_id
                 where u.deleted = 0
                """);
        if (byUserId) {
            sql.append(" and u.id = #{userId}\n");
        } else {
            appendQueryFilters(sql, query);
        }
        appendScopeFilter(sql, scope);
        sql.append(" order by u.id\n</script>");
        return sql.toString();
    }

    private void appendQueryFilters(StringBuilder sql, UserQuery query) {
        if (query.keyword() != null) {
            sql.append("""
                     and (
                         lower(u.username) like concat('%', lower(#{query.keyword}), '%')
                         or lower(coalesce(u.nickname, '')) like concat('%', lower(#{query.keyword}), '%')
                     )
                    """);
        }
        if (query.deptId() != null) {
            sql.append(" and u.dept_id = #{query.deptId}\n");
        }
        if (query.status() != null) {
            sql.append(" and u.status = #{query.status}\n");
        }
    }

    private void appendScopeFilter(StringBuilder sql, DepartmentScope scope) {
        if (scope.all()) {
            return;
        }
        if (scope.isNone()) {
            sql.append(" and 1 = 0\n");
            return;
        }
        sql.append(" and (\n");
        boolean hasDepartmentScope = !scope.deptIds().isEmpty();
        if (hasDepartmentScope) {
            sql.append("""
                       u.dept_id in
                       <foreach collection="scope.deptIds" item="deptId" open="(" separator="," close=")">
                           #{deptId}
                       </foreach>
                    """);
        }
        if (scope.includeSelf()) {
            if (hasDepartmentScope) {
                sql.append(" or\n");
            }
            sql.append(" u.id = #{scope.userId}\n");
        }
        sql.append(" )\n");
    }

    private <T> T requireParameter(Map<String, Object> parameters, String name, Class<T> type) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        Object value = parameters.get(name);
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("缺少参数: " + name);
        }
        return type.cast(value);
    }
}
