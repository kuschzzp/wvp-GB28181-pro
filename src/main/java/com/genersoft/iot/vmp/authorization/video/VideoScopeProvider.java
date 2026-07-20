package com.genersoft.iot.vmp.authorization.video;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

public class VideoScopeProvider {

    public String findEnabledRoleScopes(Map<String, Object> parameters) {
        requireRoleIds(parameters);
        return """
                <script>
                select r.id as roleId,
                       r.video_scope_type as scopeType
                  from wvp_role r
                 where r.status = 0
                   and r.id in
                 <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                     #{roleId}
                 </foreach>
                 order by r.id
                </script>
                """;
    }

    public String findEnabledResources(Map<String, Object> parameters) {
        requireRoleIds(parameters);
        return """
                <script>
                select distinct rgi.resource_type as resourceType,
                                rgi.resource_id as resourceId
                  from wvp_role r
                  join wvp_role_resource_group rrg
                    on rrg.role_id = r.id
                  join wvp_resource_group_closure subtree
                    on subtree.ancestor_id = rrg.resource_group_id
                  join wvp_resource_group item_group
                    on item_group.id = subtree.descendant_id
                   and item_group.status = 0
                  join wvp_resource_group_item rgi
                    on rgi.group_id = item_group.id
                 where r.status = 0
                   and r.video_scope_type = 'CUSTOM'
                   and r.id in
                 <foreach collection="roleIds" item="roleId" open="(" separator="," close=")">
                     #{roleId}
                 </foreach>
                   and not exists (
                       select 1
                         from wvp_resource_group_closure ancestor_path
                         join wvp_resource_group ancestor
                           on ancestor.id = ancestor_path.ancestor_id
                        where ancestor_path.descendant_id = item_group.id
                          and ancestor.status &lt;&gt; 0
                   )
                 order by rgi.resource_type, rgi.resource_id
                </script>
                """;
    }

    private Collection<?> requireRoleIds(Map<String, Object> parameters) {
        Objects.requireNonNull(parameters, "parameters must not be null");
        Object value = parameters.get("roleIds");
        if (!(value instanceof Collection<?> roleIds) || roleIds.isEmpty()) {
            throw new IllegalArgumentException("roleIds must not be empty");
        }
        if (roleIds.stream().anyMatch(roleId -> !(roleId instanceof Number)
                || ((Number) roleId).longValue() <= 0)) {
            throw new IllegalArgumentException("roleIds must contain only positive IDs");
        }
        return roleIds;
    }
}
