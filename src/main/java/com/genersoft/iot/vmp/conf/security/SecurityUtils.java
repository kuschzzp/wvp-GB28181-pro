package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    public static AuthPrincipal getPrincipal() {
        Authentication authentication = getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof AuthPrincipal principal) {
            return principal;
        }
        return null;
    }

    public static long getUserId() {
        AuthPrincipal principal = getPrincipal();
        if (principal == null || principal.userId() == null) {
            throw new IllegalStateException("当前请求没有用户身份");
        }
        return principal.userId();
    }
}
