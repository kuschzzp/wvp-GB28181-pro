package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentPrincipalService {

    public AuthPrincipal requireCurrent() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || !(authentication.getPrincipal() instanceof AuthPrincipal principal)) {
            throw new AuthenticationCredentialsNotFoundException("当前请求没有有效身份");
        }
        return principal;
    }
}
