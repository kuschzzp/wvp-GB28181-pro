package com.genersoft.iot.vmp.auth.user.dto;

public record UserQuery(
        int page,
        int count,
        String keyword,
        Long deptId,
        Integer status) {

    private static final int MAX_PAGE_SIZE = 200;

    public UserQuery {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于 0");
        }
        if (count < 1 || count > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("每页数量必须在 1 到 " + MAX_PAGE_SIZE + " 之间");
        }
        keyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        if (deptId != null && deptId <= 0) {
            throw new IllegalArgumentException("部门 ID 非法");
        }
        if (status != null && status != 0 && status != 1) {
            throw new IllegalArgumentException("用户状态非法");
        }
    }
}
