package com.genersoft.iot.vmp.storager.dao.dto;

import java.time.LocalDateTime;

public class Role {

    private long id;
    private String name;
    private String roleKey;
    private int roleSort;
    private String deptDataScope;
    private String videoScopeType;
    private int status;
    private int systemRole;
    private Long createBy;
    private LocalDateTime createTime;
    private Long updateBy;
    private LocalDateTime updateTime;

    /** 仅供 2.7.x 兼容表读取，不能作为授权依据。 */
    private String authority;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public int getRoleSort() {
        return roleSort;
    }

    public void setRoleSort(int roleSort) {
        this.roleSort = roleSort;
    }

    public String getDeptDataScope() {
        return deptDataScope;
    }

    public void setDeptDataScope(String deptDataScope) {
        this.deptDataScope = deptDataScope;
    }

    public String getVideoScopeType() {
        return videoScopeType;
    }

    public void setVideoScopeType(String videoScopeType) {
        this.videoScopeType = videoScopeType;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public int getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(int systemRole) {
        this.systemRole = systemRole;
    }

    public boolean isSystem() {
        return systemRole == 1;
    }

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public Long getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getAuthority() {
        return authority;
    }

    public void setAuthority(String authority) {
        this.authority = authority;
    }
}
