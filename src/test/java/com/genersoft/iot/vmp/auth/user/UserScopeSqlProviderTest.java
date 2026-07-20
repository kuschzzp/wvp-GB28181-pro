package com.genersoft.iot.vmp.auth.user;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.auth.user.dto.UserQuery;
import com.genersoft.iot.vmp.auth.user.sql.UserScopeSqlProvider;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserScopeSqlProviderTest {

    private final UserScopeSqlProvider provider = new UserScopeSqlProvider();

    @Test
    void noneUsesConstantFalseAndNeverBuildsEmptyInClause() {
        String sql = provider.query(Map.of(
                "query", query("camera"),
                "scope", DepartmentScope.none(12L)));

        assertTrue(sql.contains("and 1 = 0"));
        assertFalse(sql.toLowerCase().contains("in ()"));
        assertTrue(sql.contains("#{query.keyword}"));
        assertFalse(sql.contains("camera"));
    }

    @Test
    void selfUsesBoundUserIdWithoutDepartmentInClause() {
        String sql = provider.query(Map.of(
                "query", query(null),
                "scope", DepartmentScope.self(12L)));

        assertTrue(sql.contains("u.id = #{scope.userId}"));
        assertFalse(sql.contains("scope.deptIds"));
        assertFalse(sql.toLowerCase().contains("in ()"));
    }

    @Test
    void departmentUnionUsesForeachBindingsAndNeverEmbedsIds() {
        String sql = provider.query(Map.of(
                "query", query(null),
                "scope", new DepartmentScope(false, Set.of(101L, 202L), true, 12L)));

        assertTrue(sql.contains("collection=\"scope.deptIds\""));
        assertTrue(sql.contains("#{deptId}"));
        assertTrue(sql.contains("u.id = #{scope.userId}"));
        assertFalse(sql.contains("101"));
        assertFalse(sql.contains("202"));
    }

    @Test
    void allDoesNotAddARestrictiveScopePredicate() {
        String sql = provider.query(Map.of(
                "query", query(null),
                "scope", DepartmentScope.all(12L)));

        assertFalse(sql.contains("scope.deptIds"));
        assertFalse(sql.contains("scope.userId"));
        assertFalse(sql.contains("and 1 = 0"));
    }

    private UserQuery query(String keyword) {
        return new UserQuery(1, 20, keyword, null, null);
    }
}
