package com.genersoft.iot.vmp.storager.dao.provider;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;

import java.util.Map;
import java.util.Objects;

public class AuthzAuditProvider {

    public String query(Map<String, Object> parameters) {
        AuthorizationAuditQuery query = requireParameter(
                parameters, "query", AuthorizationAuditQuery.class);
        DepartmentScope scope = requireParameter(
                parameters, "scope", DepartmentScope.class);
        StringBuilder sql = new StringBuilder("""
                <script>
                select a.id,
                       a.principal_type as principalType,
                       a.principal_id as principalId,
                       a.principal_dept_id as principalDeptId,
                       a.permission_code as permissionCode,
                       a.resource_type as resourceType,
                       a.resource_id as resourceId,
                       a.decision,
                       a.reason_code as reasonCode,
                       a.request_id as requestId,
                       a.client_ip as clientIp,
                       a.create_time as createTime
                  from wvp_authz_audit a
                 where 1 = 1
                """);
        appendQueryFilters(sql, query);
        appendScopeFilter(sql, scope);
        sql.append(" order by a.create_time desc, a.id desc\n</script>");
        return sql.toString();
    }

    private void appendQueryFilters(StringBuilder sql, AuthorizationAuditQuery query) {
        if (query.permissionCode() != null) {
            sql.append(" and a.permission_code = #{query.permissionCode}\n");
        }
        if (query.decision() != null) {
            sql.append(" and a.decision = #{query.decision}\n");
        }
        if (query.principalType() != null) {
            sql.append(" and a.principal_type = #{query.principalType}\n");
        }
        if (query.principalId() != null) {
            sql.append(" and a.principal_id = #{query.principalId}\n");
        }
        if (query.resourceType() != null) {
            sql.append(" and a.resource_type = #{query.resourceType}\n");
        }
        if (query.resourceId() != null) {
            sql.append(" and a.resource_id = #{query.resourceId}\n");
        }
        if (query.startTime() != null) {
            sql.append(" and a.create_time &gt;= #{query.startTime}\n");
        }
        if (query.endTime() != null) {
            sql.append(" and a.create_time &lt;= #{query.endTime}\n");
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
                       a.principal_dept_id in
                       <foreach collection="scope.deptIds" item="deptId" open="(" separator="," close=")">
                           #{deptId}
                       </foreach>
                    """);
        }
        if (scope.includeSelf()) {
            if (hasDepartmentScope) {
                sql.append(" or\n");
            }
            sql.append(" (a.principal_type = 'USER' and a.principal_id = #{scope.userId})\n");
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
