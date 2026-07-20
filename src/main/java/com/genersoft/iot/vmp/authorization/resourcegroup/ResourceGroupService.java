package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.authorization.resourcegroup.dto.CreateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.UpdateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroupItem;
import com.genersoft.iot.vmp.authorization.video.model.VideoResourceRef;
import com.genersoft.iot.vmp.authorization.video.model.VideoScopeType;

import java.util.List;
import java.util.Set;

public interface ResourceGroupService {

    List<ResourceGroupTreeNode> tree();

    List<ResourceGroupItem> findItems(long groupId);

    Set<Long> findRoleGroupIds(long roleId);

    long create(CreateResourceGroupCommand command);

    void move(long groupId, long parentId);

    void move(long groupId, long parentId, Long operatorId);

    void update(UpdateResourceGroupCommand command);

    void delete(long groupId, Long migrationTargetGroupId, Long operatorId);

    void replaceItems(long groupId, Set<VideoResourceRef> items);

    void replaceItems(long groupId, Set<VideoResourceRef> items, Long operatorId);

    void replaceRoleGroups(long roleId, VideoScopeType type, Set<Long> groupIds);

    void replaceRoleGroups(
            long roleId,
            VideoScopeType type,
            Set<Long> groupIds,
            Long operatorId);

    /**
     * Used only by the outer role-save transaction, which invalidates affected users once
     * after menus, departments and video groups have all been persisted.
     */
    void replaceRoleGroupsForRoleUpdate(
            long roleId,
            VideoScopeType type,
            Set<Long> groupIds,
            Long operatorId);
}
