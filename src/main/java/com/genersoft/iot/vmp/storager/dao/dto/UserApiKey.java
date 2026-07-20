package com.genersoft.iot.vmp.storager.dao.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.genersoft.iot.vmp.auth.apikey.ApiKeyStatus;

import java.io.Serializable;
import java.time.LocalDateTime;

public class UserApiKey implements Serializable {

    private long id;
    private long userId;
    private String app;
    @JsonIgnore
    private String keyHash;
    private String keyPrefix;
    private String keyLast4;
    @JsonIgnore
    private String legacyApiKey;
    private Long expiredAt;
    private String remark;
    private ApiKeyStatus status;
    private String ipAllowlist;
    private long authzVersion;
    private LocalDateTime lastUsedAt;
    private String createTime;
    private String updateTime;
    private String username;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }
    public long getUserId() { return userId; }
    public void setUserId(long userId) { this.userId = userId; }
    public String getApp() { return app; }
    public void setApp(String app) { this.app = app; }
    public String getKeyHash() { return keyHash; }
    public void setKeyHash(String keyHash) { this.keyHash = keyHash; }
    public String getKeyPrefix() { return keyPrefix; }
    public void setKeyPrefix(String keyPrefix) { this.keyPrefix = keyPrefix; }
    public String getKeyLast4() { return keyLast4; }
    public void setKeyLast4(String keyLast4) { this.keyLast4 = keyLast4; }
    public String getLegacyApiKey() { return legacyApiKey; }
    public void setLegacyApiKey(String legacyApiKey) { this.legacyApiKey = legacyApiKey; }
    public Long getExpiredAt() { return expiredAt; }
    public void setExpiredAt(Long expiredAt) { this.expiredAt = expiredAt; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public ApiKeyStatus getStatus() { return status; }
    public void setStatus(ApiKeyStatus status) { this.status = status; }
    public String getIpAllowlist() { return ipAllowlist; }
    public void setIpAllowlist(String ipAllowlist) { this.ipAllowlist = ipAllowlist; }
    public long getAuthzVersion() { return authzVersion; }
    public void setAuthzVersion(long authzVersion) { this.authzVersion = authzVersion; }
    public LocalDateTime getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(LocalDateTime lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public String getUpdateTime() { return updateTime; }
    public void setUpdateTime(String updateTime) { this.updateTime = updateTime; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}
