package com.genersoft.iot.vmp.authorization.video;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.authorization.video.model.VideoScope;

public interface VideoResourceScopeService {

    VideoScope resolveDecisionScope(AuthPrincipal principal, String permissionCode);

    VideoScope resolveEffectiveScope(AuthPrincipal principal, String permissionCode);
}
