package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.authorization.video.model.ResourceDenialMode;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;

import java.util.Collection;

public interface ResourceAuthorizationService {

    void requireVideoResource(
            AuthPrincipal principal,
            String permissionCode,
            VideoResourceRef resource,
            ResourceDenialMode denialMode);

    void requireAllVideoResources(
            AuthPrincipal principal,
            String permissionCode,
            Collection<VideoResourceRef> resources);
}
