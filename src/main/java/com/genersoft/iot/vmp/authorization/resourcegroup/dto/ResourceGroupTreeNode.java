package com.genersoft.iot.vmp.authorization.resourcegroup.dto;

import java.util.List;

public record ResourceGroupTreeNode(
        long id,
        long parentId,
        String groupCode,
        String groupName,
        int orderNum,
        int status,
        String description,
        List<ResourceGroupTreeNode> children) {

    public ResourceGroupTreeNode {
        children = children == null ? List.of() : List.copyOf(children);
    }
}
