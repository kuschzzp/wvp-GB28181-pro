package com.genersoft.iot.vmp.vmanager.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.genersoft.iot.vmp.auth.dto.LoginResponse;
import com.genersoft.iot.vmp.conf.security.dto.LoginUser;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserAuthorizationTest {

    @Test
    void userAdministrationEndpointsUseMethodPermissionsInsteadOfFixedRoleIds() {
        Set<String> protectedMethods = Set.of(
                "create", "addLegacy", "delete", "deleteByPath", "all", "users",
                "assignRoles", "changePushKey", "resetPassword");

        Arrays.stream(UserController.class.getDeclaredMethods())
                .filter(method -> protectedMethods.contains(method.getName()))
                .forEach(this::assertPreAuthorized);
    }

    @Test
    void persistentUserAndLoginResponseNeverSerializeCredentials() throws Exception {
        User user = new User();
        user.setId(12L);
        user.setUsername("operator");
        user.setPassword("secret-hash");
        user.setPushKey("push-secret");
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        JsonNode userJson = mapper.valueToTree(user);
        JsonNode loginJson = mapper.valueToTree(new LoginResponse(
                "token", Instant.parse("2026-07-13T12:00:00Z"), "wvp-1"));

        assertFalse(userJson.has("password"));
        assertFalse(userJson.has("pushKey"));
        assertFalse(userJson.toString().contains("push-secret"));
        assertFalse(loginJson.has("password"));
        assertFalse(loginJson.has("credentials"));
    }

    @Test
    void authenticationUserCopiesOnlyTemporaryAuthenticationFields() {
        User user = new User();
        user.setId(12L);
        user.setUsername("operator");
        user.setPassword("temporary-password-hash");
        user.setPushKey("push-secret");

        LoginUser loginUser = new LoginUser(user);

        assertFalse(Arrays.stream(LoginUser.class.getDeclaredFields())
                .anyMatch(field -> User.class.isAssignableFrom(field.getType())));
        assertThrows(NoSuchMethodException.class,
                () -> LoginUser.class.getMethod("getPushKey"));
        loginUser.eraseCredentials();
        assertNull(loginUser.getPassword());
    }

    private void assertPreAuthorized(Method method) {
        assertNotNull(method.getAnnotation(PreAuthorize.class), method::toGenericString);
    }
}
