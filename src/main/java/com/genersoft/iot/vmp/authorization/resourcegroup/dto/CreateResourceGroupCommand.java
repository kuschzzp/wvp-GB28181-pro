package com.genersoft.iot.vmp.authorization.resourcegroup.dto;

public record CreateResourceGroupCommand(
        long parentId,
        String groupCode,
        String groupName,
        int orderNum,
        int status,
        String description,
        Long operatorId) {
}
