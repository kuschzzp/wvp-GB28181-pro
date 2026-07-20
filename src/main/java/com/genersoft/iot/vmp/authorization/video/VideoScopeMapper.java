package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.authorization.video.model.VideoRoleScopeGrant;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeResourceRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.SelectProvider;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Mapper
@Repository
public interface VideoScopeMapper {

    @SelectProvider(type = VideoScopeProvider.class, method = "findEnabledRoleScopes")
    List<VideoRoleScopeGrant> findEnabledRoleScopes(
            @Param("roleIds") Collection<Long> roleIds);

    @SelectProvider(type = VideoScopeProvider.class, method = "findEnabledResources")
    List<VideoScopeResourceRow> findEnabledResources(
            @Param("roleIds") Collection<Long> roleIds);
}
