package com.genersoft.iot.vmp.storager.dao;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

@Mapper
@Repository
public interface PermissionQueryMapper {

    @Select("""
            select count(*)
              from wvp_user u
              join wvp_user_role_rel ur on ur.user_id = u.id
              join wvp_role r on r.id = ur.role_id
             where u.id = #{userId}
               and u.status = 0
               and u.locked = 0
               and u.deleted = 0
               and r.status = 0
               and r.system_role = 1
            """)
    int countSystemRoles(@Param("userId") long userId);

    @Select("""
            select distinct m.perms
              from wvp_user u
              join wvp_user_role_rel ur on ur.user_id = u.id
              join wvp_role r on r.id = ur.role_id and r.status = 0
              join wvp_role_menu rm on rm.role_id = r.id
              join wvp_menu m on m.id = rm.menu_id and m.status = 0
             where u.id = #{userId}
               and u.status = 0
               and u.locked = 0
               and u.deleted = 0
               and m.perms is not null
               and m.perms <> ''
             order by m.perms
            """)
    List<String> findPermissionCodes(@Param("userId") long userId);

    @Select("""
            select distinct perms
              from wvp_menu
             where status = 0
               and perms is not null
               and perms <> ''
             order by perms
            """)
    List<String> findAllEnabledPermissionCodes();

    @Select("""
            select distinct r.id
              from wvp_user u
              join wvp_user_role_rel ur on ur.user_id = u.id
              join wvp_role r on r.id = ur.role_id and r.status = 0
             where u.id = #{userId}
               and u.status = 0
               and u.locked = 0
               and u.deleted = 0
             order by r.id
            """)
    List<Long> findEnabledRoleIds(@Param("userId") long userId);

    @Select("""
            select distinct r.id
              from wvp_user u
              join wvp_user_role_rel ur on ur.user_id = u.id
              join wvp_role r on r.id = ur.role_id and r.status = 0
              join wvp_role_menu rm on rm.role_id = r.id
              join wvp_menu m on m.id = rm.menu_id and m.status = 0
             where u.id = #{userId}
               and u.status = 0
               and u.locked = 0
               and u.deleted = 0
               and m.perms = #{permissionCode}
             order by r.id
            """)
    List<Long> findGrantingRoleIds(
            @Param("userId") long userId,
            @Param("permissionCode") String permissionCode);
}
