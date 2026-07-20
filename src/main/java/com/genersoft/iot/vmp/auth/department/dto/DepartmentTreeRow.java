package com.genersoft.iot.vmp.auth.department.dto;

public class DepartmentTreeRow {

    private long id;
    private long parentId;
    private String deptName;
    private String deptCode;
    private int orderNum;
    private Long leaderUserId;
    private int status;
    private boolean readOnlyPath;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(long parentId) {
        this.parentId = parentId;
    }

    public String getDeptName() {
        return deptName;
    }

    public void setDeptName(String deptName) {
        this.deptName = deptName;
    }

    public String getDeptCode() {
        return deptCode;
    }

    public void setDeptCode(String deptCode) {
        this.deptCode = deptCode;
    }

    public int getOrderNum() {
        return orderNum;
    }

    public void setOrderNum(int orderNum) {
        this.orderNum = orderNum;
    }

    public Long getLeaderUserId() {
        return leaderUserId;
    }

    public void setLeaderUserId(Long leaderUserId) {
        this.leaderUserId = leaderUserId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean isReadOnlyPath() {
        return readOnlyPath;
    }

    public void setReadOnlyPath(boolean readOnlyPath) {
        this.readOnlyPath = readOnlyPath;
    }
}
