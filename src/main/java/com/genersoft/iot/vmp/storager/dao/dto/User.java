package com.genersoft.iot.vmp.storager.dao.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Set;

public class User {

    private long id;
    private String username;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;
    private String createTime;
    private String updateTime;
    @JsonIgnore
    private String pushKey;
    private Role role;
    private Long deptId;
    private String nickname;
    private int status;
    private boolean locked;
    private boolean deleted;
    private long tokenVersion;
    private int passwordVersion;
    private LocalDateTime lastLoginAt;
    private Long createBy;
    private Long updateBy;
    private Set<Long> roleIds = Set.of();
    private boolean systemUser;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public String getPushKey() {
        return pushKey;
    }

    public void setPushKey(String pushKey) {
        this.pushKey = pushKey;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    public boolean isDeleted() {
        return deleted;
    }

    public void setDeleted(boolean deleted) {
        this.deleted = deleted;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(long tokenVersion) {
        this.tokenVersion = tokenVersion;
    }

    public int getPasswordVersion() {
        return passwordVersion;
    }

    public void setPasswordVersion(int passwordVersion) {
        this.passwordVersion = passwordVersion;
    }

    public LocalDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    public Long getCreateBy() {
        return createBy;
    }

    public void setCreateBy(Long createBy) {
        this.createBy = createBy;
    }

    public Long getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(Long updateBy) {
        this.updateBy = updateBy;
    }

    public Set<Long> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(Set<Long> roleIds) {
        this.roleIds = roleIds == null ? Set.of() : Set.copyOf(roleIds);
    }

    public boolean isSystemUser() {
        return systemUser;
    }

    public void setSystemUser(boolean systemUser) {
        this.systemUser = systemUser;
    }
}
