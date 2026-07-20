package com.genersoft.iot.vmp.storager.dao;

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
public interface ApiKeyPermissionMapper {

    @Select("""
            select distinct m.perms
              from wvp_api_key_permission akp
              join wvp_menu m on m.id = akp.menu_id
             where akp.api_key_id = #{apiKeyId}
               and m.status = 0
               and m.perms is not null
               and m.perms <> ''
             order by m.perms
            """)
    List<String> findEnabledPermissionCodes(@Param("apiKeyId") long apiKeyId);

    @Select("""
            <script>
            select count(*)
              from wvp_menu
             where status = 0
               and perms is not null
               and perms &lt;&gt; ''
               and id in
             <foreach collection="menuIds" item="menuId" open="(" separator="," close=")">
                 #{menuId}
             </foreach>
            </script>
            """)
    int countEnabledPermissionMenus(@Param("menuIds") Collection<Long> menuIds);

    @Select("""
            <script>
            select perms
              from wvp_menu
             where status = 0
               and perms is not null
               and perms &lt;&gt; ''
               and id in
             <foreach collection="menuIds" item="menuId" open="(" separator="," close=")">
                 #{menuId}
             </foreach>
             order by perms
            </script>
            """)
    List<String> findEnabledPermissionCodesByMenuIds(
            @Param("menuIds") Collection<Long> menuIds);

    @Select("""
            select menu_id
              from wvp_api_key_permission
             where api_key_id = #{apiKeyId}
             order by menu_id
            """)
    List<Long> findMenuIds(@Param("apiKeyId") long apiKeyId);

    @Delete("delete from wvp_api_key_permission where api_key_id = #{apiKeyId}")
    int deleteByApiKeyId(@Param("apiKeyId") long apiKeyId);

    @Insert("insert into wvp_api_key_permission (api_key_id, menu_id) values (#{apiKeyId}, #{menuId})")
    int insert(@Param("apiKeyId") long apiKeyId, @Param("menuId") long menuId);
}
