package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;

import java.util.Map;
import java.util.Objects;

/**
 * Shared SQL fragment builder for permission-coupled video list queries.
 *
 * <p>The aliases passed to this class are constants owned by mapper providers, never request
 * values. All role and resource IDs remain MyBatis bind variables.</p>
 */
public final class VideoListScopeSql {

    private VideoListScopeSql() {
    }

    public static void appendDeviceScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            String deviceAlias) {
        requireAlias(deviceAlias);
        appendScope(sql, parameters, """
                (
                    (rgi.resource_type = 'GB_DEVICE'
                     and rgi.resource_id = %1$s.id)
                    or
                    (rgi.resource_type = 'CHANNEL'
                     and exists (
                         select 1
                           from wvp_device_channel authorized_channel
                          where authorized_channel.data_type = 1
                            and authorized_channel.data_device_id = %1$s.id
                            and authorized_channel.id = rgi.resource_id
                     ))
                )
                """.formatted(deviceAlias));
    }

    public static void appendChannelScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            String channelAlias) {
        requireAlias(channelAlias);
        appendScope(sql, parameters, """
                (
                    (rgi.resource_type = 'CHANNEL'
                     and rgi.resource_id = %1$s.id)
                    or
                    (rgi.resource_type = 'GB_DEVICE'
                     and %1$s.data_type = 1
                     and rgi.resource_id = %1$s.data_device_id)
                    or
                    (rgi.resource_type = 'STREAM_PUSH'
                     and %1$s.data_type = 2
                     and rgi.resource_id = %1$s.data_device_id)
                    or
                    (rgi.resource_type = 'STREAM_PROXY'
                     and %1$s.data_type = 3
                     and rgi.resource_id = %1$s.data_device_id)
                )
                """.formatted(channelAlias));
    }

    public static void appendSourceScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            VideoResourceType sourceType,
            String sourceAlias,
            String channelAlias) {
        Objects.requireNonNull(sourceType, "sourceType must not be null");
        if (sourceType != VideoResourceType.STREAM_PROXY
                && sourceType != VideoResourceType.STREAM_PUSH) {
            throw new IllegalArgumentException("列表来源类型不受支持: " + sourceType);
        }
        requireAlias(sourceAlias);
        requireAlias(channelAlias);
        appendScope(sql, parameters, """
                (
                    (rgi.resource_type = '%1$s'
                     and rgi.resource_id = %2$s.id)
                    or
                    (rgi.resource_type = 'CHANNEL'
                     and %3$s.id is not null
                     and rgi.resource_id = %3$s.id)
                )
                """.formatted(sourceType.name(), sourceAlias, channelAlias));
    }

    public static void appendJtTerminalScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            String terminalAlias) {
        requireAlias(terminalAlias);
        appendScope(sql, parameters, """
                (
                    (rgi.resource_type = 'JT_TERMINAL'
                     and rgi.resource_id = %1$s.id)
                    or
                    (rgi.resource_type = 'JT_CHANNEL'
                     and exists (
                         select 1
                           from wvp_jt_channel authorized_jt_channel
                          where authorized_jt_channel.terminal_db_id = %1$s.id
                            and authorized_jt_channel.id = rgi.resource_id
                     ))
                )
                """.formatted(terminalAlias));
    }

    public static void appendJtChannelScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            String channelAlias) {
        requireAlias(channelAlias);
        appendScope(sql, parameters, """
                (
                    (rgi.resource_type = 'JT_CHANNEL'
                     and rgi.resource_id = %1$s.id)
                    or
                    (rgi.resource_type = 'JT_TERMINAL'
                     and rgi.resource_id = %1$s.terminal_db_id)
                )
                """.formatted(channelAlias));
    }

    public static void appendCloudRecordScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            String recordAlias,
            String channelAlias) {
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        requireAlias(recordAlias);
        requireAlias(channelAlias);
        VideoScopeQuery scope = requireScope(parameters);
        String legacy = "coalesce(%1$s.source_type, 'LEGACY_UNBOUND') = 'LEGACY_UNBOUND'"
                .formatted(recordAlias);
        String bound = "coalesce(%1$s.source_type, 'LEGACY_UNBOUND') &lt;&gt; 'LEGACY_UNBOUND'"
                .formatted(recordAlias);

        if (scope.all()) {
            if (!scope.systemUser()) {
                sql.append(" AND ").append(bound);
            }
            return;
        }
        if (scope.isNone() || scope.grantingRoleIds().isEmpty()) {
            if (scope.systemUser()) {
                sql.append(" AND ").append(legacy);
            } else {
                sql.append(" AND 1=0 ");
            }
            return;
        }

        String resourceMatcher = """
                (
                    (rgi.resource_type = %1$s.source_type
                     and rgi.resource_id = %1$s.source_id)
                    or
                    (rgi.resource_type = 'CHANNEL'
                     and rgi.resource_id = %2$s.id)
                    or
                    (rgi.resource_type = 'GB_DEVICE'
                     and %2$s.data_type = 1
                     and rgi.resource_id = %2$s.data_device_id)
                    or
                    (rgi.resource_type = 'STREAM_PUSH'
                     and %2$s.data_type = 2
                     and rgi.resource_id = %2$s.data_device_id)
                    or
                    (rgi.resource_type = 'STREAM_PROXY'
                     and %2$s.data_type = 3
                     and rgi.resource_id = %2$s.data_device_id)
                )
                """.formatted(recordAlias, channelAlias);
        if (scope.systemUser()) {
            sql.append(" AND (").append(legacy).append(" OR (").append(bound).append(" AND ");
            appendRoleScopeExists(sql, resourceMatcher);
            sql.append(")) ");
        } else {
            sql.append(" AND ").append(bound).append(" AND ");
            appendRoleScopeExists(sql, resourceMatcher);
        }
    }

    public static String asScript(String sql) {
        return "<script>\n" + sql + "\n</script>";
    }

    private static void appendScope(
            StringBuilder sql,
            Map<String, Object> parameters,
            String resourceMatcher) {
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(parameters, "parameters must not be null");
        VideoScopeQuery scope = requireScope(parameters);
        if (scope.all()) {
            return;
        }
        if (scope.isNone() || scope.grantingRoleIds().isEmpty()) {
            sql.append(" AND 1=0 ");
            return;
        }
        sql.append(" AND ");
        appendRoleScopeExists(sql, resourceMatcher);
    }

    private static void appendRoleScopeExists(
            StringBuilder sql,
            String resourceMatcher) {
        sql.append("""
                 exists (
                     select 1
                       from wvp_role role_scope
                       join wvp_role_resource_group rrg
                         on rrg.role_id = role_scope.id
                       join wvp_resource_group_closure subtree
                         on subtree.ancestor_id = rrg.resource_group_id
                       join wvp_resource_group item_group
                         on item_group.id = subtree.descendant_id
                        and item_group.status = 0
                       join wvp_resource_group_item rgi
                         on rgi.group_id = item_group.id
                      where role_scope.status = 0
                        and role_scope.video_scope_type = 'CUSTOM'
                        and role_scope.id in
                       <foreach collection="scope.grantingRoleIds" item="scopeRoleId"
                                open="(" separator="," close=")">
                           #{scopeRoleId}
                       </foreach>
                        and not exists (
                            select 1
                              from wvp_resource_group_closure ancestor_path
                              join wvp_resource_group ancestor
                                on ancestor.id = ancestor_path.ancestor_id
                             where ancestor_path.descendant_id = item_group.id
                               and ancestor.status &lt;&gt; 0
                        )
                        and
                """);
        sql.append(resourceMatcher);
        sql.append(" ) ");
    }

    private static VideoScopeQuery requireScope(Map<String, Object> parameters) {
        Object value = parameters.get("scope");
        if (!(value instanceof VideoScopeQuery scope)) {
            throw new IllegalArgumentException("scope must be a VideoScopeQuery");
        }
        return scope;
    }

    private static void requireAlias(String alias) {
        if (alias == null || !alias.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("SQL alias is invalid");
        }
    }
}
