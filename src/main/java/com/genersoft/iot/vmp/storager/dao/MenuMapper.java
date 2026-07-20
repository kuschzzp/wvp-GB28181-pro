package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.storager.dao.dto.Menu;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface MenuMapper {

    @Select("select menu_id from wvp_role_menu where role_id = #{roleId} order by menu_id")
    List<Long> findMenuIdsByRoleId(@Param("roleId") long roleId);

    String MENU_COLUMNS = """
            id, parent_id as parentId, menu_name as menuName, menu_type as menuType,
            route_name as routeName, path, component, perms, icon, order_num as orderNum,
            visible, status, cacheable, external, system_menu as systemMenu
            """;

    @Select("select " + MENU_COLUMNS + " from wvp_menu where status = 0 order by order_num, id")
    List<Menu> findAllEnabled();

    @Select("select " + MENU_COLUMNS + " from wvp_menu order by parent_id, order_num, id")
    List<Menu> findAll();

    @Select("select " + MENU_COLUMNS + " from wvp_menu where id = #{id}")
    Menu findById(@Param("id") long id);

    @Select("select " + MENU_COLUMNS + " from wvp_menu where id = #{id} for update")
    Menu findByIdForUpdate(@Param("id") long id);

    @Select("""
            select distinct m.id, m.parent_id as parentId, m.menu_name as menuName,
                   m.menu_type as menuType, m.route_name as routeName, m.path, m.component,
                   m.perms, m.icon, m.order_num as orderNum, m.visible, m.status,
                   m.cacheable, m.external, m.system_menu as systemMenu
              from wvp_menu m
              join wvp_role_menu rm on rm.menu_id = m.id
              join wvp_user_role_rel ur on ur.role_id = rm.role_id
              join wvp_role r on r.id = ur.role_id and r.status = 0
             where ur.user_id = #{userId}
               and m.status = 0
             order by m.order_num, m.id
            """)
    List<Menu> findEnabledForUser(@Param("userId") long userId);

    @Select("""
            <script>
            select count(*) from wvp_menu
             where status = 0
               and id in
             <foreach collection="menuIds" item="menuId" open="(" separator="," close=")">
                 #{menuId}
             </foreach>
            </script>
            """)
    int countEnabledByIds(@Param("menuIds") Collection<Long> menuIds);

    @Select("select count(*) from wvp_menu where parent_id = #{menuId}")
    int countChildren(@Param("menuId") long menuId);

    @Select("select count(*) from wvp_menu where perms = #{perms}")
    int countByPermission(@Param("perms") String permission);

    @Select("select count(*) from wvp_menu where perms = #{perms} and id != #{id}")
    int countByPermissionExcludingId(
            @Param("perms") String permission,
            @Param("id") long id);

    @Select("""
            select distinct ur.user_id
              from wvp_role_menu rm
              join wvp_user_role_rel ur on ur.role_id = rm.role_id
              join wvp_user u on u.id = ur.user_id
             where rm.menu_id = #{menuId}
               and u.deleted = 0
             order by ur.user_id
            """)
    List<Long> findUserIdsByMenuId(@Param("menuId") long menuId);

    @Insert("""
            insert into wvp_menu
                (parent_id, menu_name, menu_type, route_name, path, component, perms, icon,
                 order_num, visible, status, cacheable, external, system_menu,
                 create_by, create_time, update_by, update_time)
            values
                (#{menu.parentId}, #{menu.menuName}, #{menu.menuType}, #{menu.routeName},
                 #{menu.path}, #{menu.component}, #{menu.perms}, #{menu.icon},
                 #{menu.orderNum}, #{menu.visible}, #{menu.status}, #{menu.cacheable},
                 #{menu.external}, #{menu.systemMenu}, #{operatorId}, #{now},
                 #{operatorId}, #{now})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "menu.id", keyColumn = "id")
    int insert(
            @Param("menu") Menu menu,
            @Param("operatorId") long operatorId,
            @Param("now") LocalDateTime now);

    @Update("""
            update wvp_menu
               set parent_id = #{menu.parentId},
                   menu_name = #{menu.menuName},
                   menu_type = #{menu.menuType},
                   route_name = #{menu.routeName},
                   path = #{menu.path},
                   component = #{menu.component},
                   perms = #{menu.perms},
                   icon = #{menu.icon},
                   order_num = #{menu.orderNum},
                   visible = #{menu.visible},
                   status = #{menu.status},
                   cacheable = #{menu.cacheable},
                   external = #{menu.external},
                   update_by = #{operatorId},
                   update_time = #{now}
             where id = #{menu.id}
            """)
    int update(
            @Param("menu") Menu menu,
            @Param("operatorId") long operatorId,
            @Param("now") LocalDateTime now);

    @Delete("delete from wvp_role_menu where menu_id = #{menuId}")
    int deleteRoleMenusByMenuId(@Param("menuId") long menuId);

    @Delete("delete from wvp_menu where id = #{menuId}")
    int deleteById(@Param("menuId") long menuId);

    @Delete("delete from wvp_role_menu where role_id = #{roleId}")
    int deleteRoleMenus(@Param("roleId") long roleId);

    @Insert("""
            <script>
            insert into wvp_role_menu (role_id, menu_id) values
            <foreach collection="menuIds" item="menuId" separator=",">
                (#{roleId}, #{menuId})
            </foreach>
            </script>
            """)
    int insertRoleMenus(@Param("roleId") long roleId, @Param("menuIds") Collection<Long> menuIds);
}
