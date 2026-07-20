package com.genersoft.iot.vmp.auth.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.conf.security.AnonymousAuthenticationEntryPoint;
import com.genersoft.iot.vmp.media.security.MediaAccessProperties;
import com.genersoft.iot.vmp.media.security.DownloadAccessProperties;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationService;
import com.genersoft.iot.vmp.media.security.MediaHookReplayGuard;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
        AuthorizationProperties.class,
        MediaAccessProperties.class,
        DownloadAccessProperties.class
})
public class AuthorizationConfiguration {

    @Bean
    MediaHookAuthenticationFilter mediaHookAuthenticationFilter(
            MediaHookAuthenticationService authenticationService,
            MediaHookReplayGuard replayGuard,
            ObjectMapper objectMapper,
            AnonymousAuthenticationEntryPoint authenticationEntryPoint) {
        return new MediaHookAuthenticationFilter(
                authenticationService,
                replayGuard,
                objectMapper,
                authenticationEntryPoint);
    }

    /**
     * 该 Filter 只属于 Spring Security 的媒体 Hook 链，不能再由 Servlet
     * 容器全局注册，否则普通请求会绕过 securityMatcher 被重复过滤。
     */
    @Bean
    FilterRegistrationBean<MediaHookAuthenticationFilter> mediaHookFilterRegistration(
            MediaHookAuthenticationFilter filter) {
        FilterRegistrationBean<MediaHookAuthenticationFilter> registration =
                new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }
}
