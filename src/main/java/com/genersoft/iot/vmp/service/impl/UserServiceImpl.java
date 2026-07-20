package com.genersoft.iot.vmp.service.impl;

import com.genersoft.iot.vmp.auth.repository.PermissionCache;
import com.genersoft.iot.vmp.service.IUserService;
import com.genersoft.iot.vmp.storager.dao.UserMapper;
import com.genersoft.iot.vmp.storager.dao.UserRoleMapper;
import com.genersoft.iot.vmp.storager.dao.dto.User;
import com.genersoft.iot.vmp.utils.DateUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserServiceImpl implements IUserService {

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final PermissionCache permissionCache;

    public UserServiceImpl(
            UserMapper userMapper,
            UserRoleMapper userRoleMapper,
            PermissionCache permissionCache) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.permissionCache = permissionCache;
    }

    @Override
    public User getUserById(long id) {
        return enrich(userMapper.selectById(id));
    }

    @Override
    public User getUserByUsername(String username) {
        return enrich(userMapper.getUserByUsername(username));
    }

    @Override
    public int addUser(User user) {
        User userByUsername = userMapper.getUserByUsername(user.getUsername());
        if (userByUsername != null) {
            return 0;
        }
        return userMapper.add(user);
    }
    @Override
    public boolean softDelete(long userId, long operatorId) {
        boolean deleted = userMapper.softDelete(userId, operatorId, DateUtil.getNow()) > 0;
        if (deleted) {
            evictPermissionCacheAfterCommit(userId);
        }
        return deleted;
    }

    @Override
    public List<User> getAllUsers() {
        return enrich(userMapper.selectAll());
    }

    @Override
    public boolean checkPushAuthority(String callId, String sign) {

        List<User> users = userMapper.getUsers();
        if (users.size() == 0)  {
            return false;
        }
        for (User user : users) {
            if (user.getPushKey() == null) {
                continue;
            }
            String checkStr = callId == null? user.getPushKey():(callId + "_" + user.getPushKey())  ;
            String checkSign = DigestUtils.md5DigestAsHex(checkStr.getBytes());
            if (checkSign.equals(sign)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public PageInfo<User> getUsers(int page, int count) {
        PageHelper.startPage(page, count);
        List<User> users = userMapper.getUsers();
        enrich(users);
        return new PageInfo<>(users);
    }

    @Override
    public int changePushKey(long id, String pushKey) {
        return userMapper.changePushKey(id,pushKey);
    }

    @Override
    public boolean upgradePassword(long userId, String expectedPassword, String newPassword, int passwordVersion) {
        boolean upgraded = userMapper.upgradePassword(
                userId,
                expectedPassword,
                newPassword,
                passwordVersion,
                DateUtil.getNow()) > 0;
        if (upgraded) {
            evictPermissionCacheAfterCommit(userId);
        }
        return upgraded;
    }

    @Override
    public void updateLastLogin(long userId, LocalDateTime lastLoginAt) {
        userMapper.updateLastLogin(userId, lastLoginAt);
    }

    private User enrich(User user) {
        if (user != null) {
            user.setRoleIds(new java.util.LinkedHashSet<>(userRoleMapper.findRoleIdsByUserId(user.getId())));
        }
        return user;
    }

    private List<User> enrich(List<User> users) {
        users.forEach(this::enrich);
        return users;
    }

    private void evictPermissionCacheAfterCommit(long userId) {
        Runnable eviction = () -> permissionCache.evictUser(userId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eviction.run();
                }
            });
        } else {
            eviction.run();
        }
    }
}
