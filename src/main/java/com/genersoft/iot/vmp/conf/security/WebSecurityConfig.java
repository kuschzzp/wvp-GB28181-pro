package com.genersoft.iot.vmp.conf.security;

import com.genersoft.iot.vmp.auth.security.JsonAccessDeniedHandler;
import com.genersoft.iot.vmp.auth.security.LegacyMd5PasswordEncoder;
import com.genersoft.iot.vmp.auth.security.RequestIdResolver;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyAuthenticationFilter;
import com.genersoft.iot.vmp.conf.UserSetting;
import com.genersoft.iot.vmp.media.security.MediaHookAuthenticationFilter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.CorsUtils;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class WebSecurityConfig {

    /** 媒体回调使用独立节点身份，不进入用户 JWT/API Key 认证链。 */
    static final String MEDIA_HOOK_PATH = "/index/hook/**";

    private static final String[] STATIC_AND_DOCUMENTATION_PATHS = {
            "/", "/index.html", "/favicon.ico", "/error",
            "/static/**", "/js/**", "/css/**", "/img/**", "/webjars/**",
            "/swagger-ui.html", "/swagger-ui/**", "/swagger-resources/**",
            "/doc.html", "/v3/api-docs/**"
    };

    private final UserSetting userSetting;
    private final Environment environment;
    private final DefaultUserDetailsServiceImpl userDetailsService;
    private final LogoutHandler logoutHandler;
    private final AnonymousAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ApiKeyAuthenticationFilter apiKeyAuthenticationFilter;
    private final MediaHookAuthenticationFilter mediaHookAuthenticationFilter;

    public WebSecurityConfig(
            UserSetting userSetting,
            Environment environment,
            DefaultUserDetailsServiceImpl userDetailsService,
            LogoutHandler logoutHandler,
            AnonymousAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ApiKeyAuthenticationFilter apiKeyAuthenticationFilter,
            MediaHookAuthenticationFilter mediaHookAuthenticationFilter) {
        this.userSetting = userSetting;
        this.environment = environment;
        this.userDetailsService = userDetailsService;
        this.logoutHandler = logoutHandler;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.apiKeyAuthenticationFilter = apiKeyAuthenticationFilter;
        this.mediaHookAuthenticationFilter = mediaHookAuthenticationFilter;
    }

    @PostConstruct
    void validateLegacyAuthenticationSwitch() {
        if (!Boolean.FALSE.equals(userSetting.getInterfaceAuthentication())) {
            return;
        }
        boolean developmentProfile = Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("dev")
                        || profile.endsWith("-dev")
                        || profile.equals("test")
                        || profile.endsWith("-test")
                        || profile.equals("local"));
        if (!developmentProfile) {
            throw new IllegalStateException(
                    "生产环境不允许 user-settings.interface-authentication=false");
        }
        log.warn("interface-authentication=false 已弃用；开发环境仍会执行身份认证，不再作为放行开关");
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public AuthenticationProvider authProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setHideUserNotFoundExceptions(true);
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("legacy-md5", new LegacyMd5PasswordEncoder());
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain mediaHookFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher(MEDIA_HOOK_PATH)
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(
                        mediaHookAuthenticationFilter,
                        AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(apiKeyAuthenticationFilter, JwtAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(CorsUtils::isPreFlightRequest).permitAll()
                        .requestMatchers(STATIC_AND_DOCUMENTATION_PATHS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/user/login").permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/public/share/*/play-ticket").permitAll()
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/media/download/*").permitAll()
                        // WebSocket 容器使用 30 秒单次票据认证握手，不能要求浏览器发送长期 JWT Header。
                        .requestMatchers(HttpMethod.GET, "/channel/log").permitAll()
                        // GET 登录没有控制器映射；放行到 MVC 后统一返回 405，而不是被匿名认证提前改成 401。
                        .requestMatchers(HttpMethod.GET, "/api/auth/login", "/api/user/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health/liveness").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .logout(logout -> logout
                        .logoutRequestMatcher(request -> HttpMethod.POST.matches(request.getMethod())
                                && ("/api/auth/logout".equals(request.getRequestURI())
                                || "/api/user/logout".equals(request.getRequestURI())))
                        .logoutSuccessHandler(logoutHandler)
                        .permitAll());
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedHeaders(List.of(
                "Content-Type",
                "Accept",
                JwtUtils.HEADER,
                JwtUtils.API_KEY_HEADER,
                RequestIdResolver.HEADER_NAME));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setExposedHeaders(List.of(
                JwtUtils.HEADER, "Deprecation", "Sunset", "Link"));
        configuration.setMaxAge(3600L);
        if (userSetting.getAllowedOrigins() != null && !userSetting.getAllowedOrigins().isEmpty()) {
            configuration.setAllowedOrigins(List.copyOf(userSetting.getAllowedOrigins()));
            configuration.setAllowCredentials(true);
        } else {
            configuration.setAllowedOrigins(List.of());
            configuration.setAllowCredentials(false);
        }
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
