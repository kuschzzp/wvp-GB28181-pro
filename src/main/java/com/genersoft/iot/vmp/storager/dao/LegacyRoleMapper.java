package com.genersoft.iot.vmp.storager.dao;

import com.genersoft.iot.vmp.storager.dao.dto.LegacyRole;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

@Mapper
@Repository
public interface LegacyRoleMapper {

    @Insert("""
            insert into wvp_user_role (name, authority, create_time, update_time)
            values (#{name}, #{authority}, #{createTime}, #{updateTime})
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int add(LegacyRole role);

    @Update("""
            update wvp_user_role
               set name = #{name}, authority = #{authority}, update_time = #{updateTime}
             where id = #{id}
            """)
    int update(LegacyRole role);

    @Delete("delete from wvp_user_role where id = #{id}")
    int delete(long id);
}
