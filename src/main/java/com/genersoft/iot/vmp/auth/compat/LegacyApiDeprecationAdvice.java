package com.genersoft.iot.vmp.auth.compat;

import com.genersoft.iot.vmp.vmanager.user.RoleController;
import com.genersoft.iot.vmp.vmanager.user.UserController;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice(assignableTypes = {UserController.class, RoleController.class})
public class LegacyApiDeprecationAdvice implements ResponseBodyAdvice<Object> {

    static final String SUNSET = "Thu, 01 Jul 2027 00:00:00 GMT";

    @Override
    public boolean supports(
            MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        HttpHeaders headers = response.getHeaders();
        headers.set("Deprecation", "true");
        if (!headers.containsKey("Sunset")) {
            headers.set("Sunset", SUNSET);
        }
        headers.set(HttpHeaders.LINK,
                "<" + successor(request.getURI().getPath()) + ">; rel=\"successor-version\"");
        return body;
    }

    private String successor(String path) {
        if ("/api/user/login".equals(path)) {
            return "/api/auth/login";
        }
        if ("/api/user/userInfo".equals(path)) {
            return "/api/auth/profile";
        }
        if ("/api/user/changePassword".equals(path)) {
            return "/api/system/user/profile/password";
        }
        if (path != null && path.startsWith("/api/role")) {
            return "/api/system/role";
        }
        return "/api/system/user";
    }
}
