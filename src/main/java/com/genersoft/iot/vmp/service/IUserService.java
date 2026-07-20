package com.genersoft.iot.vmp.service;

import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.github.pagehelper.PageInfo;

import java.time.LocalDateTime;
import java.util.List;

public interface IUserService {

    User getUserById(long id);

    User getUserByUsername(String username);

    int addUser(User user);

    boolean softDelete(long userId, long operatorId);

    List<User> getAllUsers();

    boolean checkPushAuthority(String callId, String sign);

    PageInfo<User> getUsers(int page, int count);

    int changePushKey(long id, String pushKey);

    boolean upgradePassword(long userId, String expectedPassword, String newPassword, int passwordVersion);

    void updateLastLogin(long userId, LocalDateTime lastLoginAt);
}
