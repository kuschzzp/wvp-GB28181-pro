package com.genersoft.iot.vmp.auth.dto;

import com.genersoft.iot.vmp.storager.dao.dto.User;

import java.time.LocalDateTime;
import java.util.Set;

public record UserSummary(
        long id,
        String username,
        String nickname,
        Long deptId,
        int status,
        boolean locked,
        Set<Long> roleIds,
        LocalDateTime lastLoginAt) {

    public static UserSummary from(User user) {
        return new UserSummary(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getDeptId(),
                user.getStatus(),
                user.isLocked(),
                user.getRoleIds(),
                user.getLastLoginAt());
    }
}
