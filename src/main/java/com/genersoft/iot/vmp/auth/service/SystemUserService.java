package com.genersoft.iot.vmp.auth.service;

import com.genersoft.iot.vmp.auth.dto.CreateUserRequest;

import java.util.Set;

public interface SystemUserService {

    long createUser(CreateUserRequest request);

    void assignRoles(long userId, Set<Long> roleIds);

    void deleteUser(long userId);

    void resetPassword(long userId, String newPassword);

    void changePushKey(long userId, String pushKey);

    void changeOwnPassword(String oldPassword, String newPassword);
}
