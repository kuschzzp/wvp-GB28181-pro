package com.genersoft.iot.vmp.authorization;

import com.genersoft.iot.vmp.auth.department.model.DepartmentScope;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeQuery;

import java.util.Collection;

public interface AuthorizationFacade {

    void requirePermission(String permissionCode);

    void requireVideoResource(
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId);

    void requireVideoResource(
            String permissionCode,
            VideoResourceType resourceType,
            long resourceId,
            ResourceDenialMode denialMode);

    void requireAllVideoResources(
            String permissionCode,
            Collection<VideoResourceRef> resources);

    VideoScope resolveVideoScope(String permissionCode);

    VideoScopeQuery resolveVideoScopeQuery(String permissionCode);

    DepartmentScope resolveDepartmentScope(String permissionCode);
}
