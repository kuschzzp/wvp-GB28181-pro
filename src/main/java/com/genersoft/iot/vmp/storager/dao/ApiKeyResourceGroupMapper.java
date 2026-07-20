package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
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
public interface ApiKeyResourceGroupMapper {

    @Select("""
            select resource_group_id
              from wvp_api_key_resource_group
             where api_key_id = #{apiKeyId}
             order by resource_group_id
            """)
    List<Long> findGroupIds(@Param("apiKeyId") long apiKeyId);

    @Select("""
            <script>
            select count(*)
              from wvp_resource_group selected_group
             where selected_group.id in
             <foreach collection="groupIds" item="groupId" open="(" separator="," close=")">
                 #{groupId}
             </foreach>
               and selected_group.status = 0
               and not exists (
                   select 1
                     from wvp_resource_group_closure ancestor_path
                     join wvp_resource_group ancestor
                       on ancestor.id = ancestor_path.ancestor_id
                    where ancestor_path.descendant_id = selected_group.id
                      and ancestor.status &lt;&gt; 0
               )
            </script>
            """)
    int countEnabledResourceGroups(@Param("groupIds") Collection<Long> groupIds);

    @Select("""
            select distinct rgi.resource_type as resourceType,
                            rgi.resource_id as resourceId
              from wvp_api_key_resource_group akg
              join wvp_resource_group_closure subtree
                on subtree.ancestor_id = akg.resource_group_id
              join wvp_resource_group item_group
                on item_group.id = subtree.descendant_id
               and item_group.status = 0
              join wvp_resource_group_item rgi
                on rgi.group_id = item_group.id
             where akg.api_key_id = #{apiKeyId}
               and not exists (
                   select 1
                     from wvp_resource_group_closure ancestor_path
                     join wvp_resource_group ancestor
                       on ancestor.id = ancestor_path.ancestor_id
                    where ancestor_path.descendant_id = item_group.id
                      and ancestor.status &lt;&gt; 0
               )
             order by rgi.resource_type, rgi.resource_id
            """)
    List<VideoScopeResourceRow> findEnabledResources(@Param("apiKeyId") long apiKeyId);

    @Select("""
            <script>
            select distinct rgi.resource_type as resourceType,
                            rgi.resource_id as resourceId
              from wvp_resource_group selected_group
              join wvp_resource_group_closure subtree
                on subtree.ancestor_id = selected_group.id
              join wvp_resource_group item_group
                on item_group.id = subtree.descendant_id
               and item_group.status = 0
              join wvp_resource_group_item rgi
                on rgi.group_id = item_group.id
             where selected_group.id in
             <foreach collection="groupIds" item="groupId" open="(" separator="," close=")">
                 #{groupId}
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
            """)
    List<VideoScopeResourceRow> findEnabledResourcesByGroupIds(
            @Param("groupIds") Collection<Long> groupIds);

    @Delete("delete from wvp_api_key_resource_group where api_key_id = #{apiKeyId}")
    int deleteByApiKeyId(@Param("apiKeyId") long apiKeyId);

    @Insert("""
            insert into wvp_api_key_resource_group (api_key_id, resource_group_id)
            values (#{apiKeyId}, #{groupId})
            """)
    int insert(@Param("apiKeyId") long apiKeyId, @Param("groupId") long groupId);
}
