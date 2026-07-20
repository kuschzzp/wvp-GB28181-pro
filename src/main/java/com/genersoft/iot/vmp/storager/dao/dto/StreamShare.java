package com.genersoft.iot.vmp.storager.dao.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceType;
import com.genersoft.iot.vmp.media.security.StreamShareStatus;

import java.time.Instant;

public class StreamShare {

    private String shareId;
    private long creatorUserId;
    private VideoResourceType resourceType;
    private long resourceId;
    private Instant expiresAt;
    private Long maxViews;
    private long viewCount;
    @JsonIgnore
    private String passcodeHash;
    private StreamShareStatus status;
    private long authzVersion;
    private Instant createTime;
    private Instant updateTime;

    public String getShareId() { return shareId; }
    public void setShareId(String shareId) { this.shareId = shareId; }
    public long getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(long creatorUserId) { this.creatorUserId = creatorUserId; }
    public VideoResourceType getResourceType() { return resourceType; }
    public void setResourceType(VideoResourceType resourceType) { this.resourceType = resourceType; }
    public long getResourceId() { return resourceId; }
    public void setResourceId(long resourceId) { this.resourceId = resourceId; }
    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }
    public Long getMaxViews() { return maxViews; }
    public void setMaxViews(Long maxViews) { this.maxViews = maxViews; }
    public long getViewCount() { return viewCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public String getPasscodeHash() { return passcodeHash; }
    public void setPasscodeHash(String passcodeHash) { this.passcodeHash = passcodeHash; }
    public StreamShareStatus getStatus() { return status; }
    public void setStatus(StreamShareStatus status) { this.status = status; }
    public long getAuthzVersion() { return authzVersion; }
    public void setAuthzVersion(long authzVersion) { this.authzVersion = authzVersion; }
    public Instant getCreateTime() { return createTime; }
    public void setCreateTime(Instant createTime) { this.createTime = createTime; }
    public Instant getUpdateTime() { return updateTime; }
    public void setUpdateTime(Instant updateTime) { this.updateTime = updateTime; }
}
