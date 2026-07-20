package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.auth.model.AuthzAuditRecord;
import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditQuery;
import com.genersoft.iot.vmp.authorization.audit.dto.AuthorizationAuditSummary;
import com.genersoft.iot.vmp.storager.dao.provider.AuthzAuditProvider;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface AuthzAuditMapper {

    @Insert("""
            insert into wvp_authz_audit
                (principal_type, principal_id, principal_dept_id, permission_code,
                 resource_type, resource_id, decision, reason_code, request_id, client_ip, create_time)
            values
                (#{principalType}, #{principalId}, #{principalDeptId}, #{permissionCode},
                 #{resourceType}, #{resourceId},
                 case when #{allowed} then 'ALLOW' else 'DENY' end,
                 #{reasonCode}, #{requestId}, #{clientIp}, #{createTime})
            """)
    int insert(AuthzAuditRecord record);

    @SelectProvider(type = AuthzAuditProvider.class, method = "query")
    List<AuthorizationAuditSummary> query(
            @Param("query") AuthorizationAuditQuery query,
            @Param("scope") DepartmentScope scope);
}
