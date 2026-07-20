package com.genersoft.iot.vmp.authorization.resourcegroup;

import com.genersoft.iot.vmp.auth.model.AuthPrincipal;
import com.genersoft.iot.vmp.auth.service.CurrentPrincipalService;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.CreateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupCreatedResponse;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupItemsRequest;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupRequest;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.ResourceGroupTreeNode;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.RoleResourceGroupRequest;
import com.genersoft.iot.vmp.authorization.resourcegroup.dto.UpdateResourceGroupCommand;
import com.genersoft.iot.vmp.authorization.resourcegroup.model.ResourceGroupItem;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/system/resource-group")
public class ResourceGroupController {

    private final ResourceGroupService resourceGroupService;
    private final CurrentPrincipalService currentPrincipalService;

    public ResourceGroupController(
            ResourceGroupService resourceGroupService,
            CurrentPrincipalService currentPrincipalService) {
        this.resourceGroupService = resourceGroupService;
        this.currentPrincipalService = currentPrincipalService;
    }

    @PreAuthorize("@authz.require('system:resource-group:list')")
    @GetMapping("/tree")
    public List<ResourceGroupTreeNode> tree() {
        return resourceGroupService.tree();
    }

    @PreAuthorize("@authz.require('system:resource-group:list')")
    @GetMapping("/{groupId}/items")
    public List<ResourceGroupItem> items(@PathVariable long groupId) {
        return resourceGroupService.findItems(groupId);
    }

    @PreAuthorize("@authz.require('system:resource-group:list')")
    @GetMapping("/role/{roleId}")
    public Set<Long> roleGroups(@PathVariable long roleId) {
        return resourceGroupService.findRoleGroupIds(roleId);
    }

    @PreAuthorize("@authz.require('system:resource-group:add')")
    @PostMapping
    public ResourceGroupCreatedResponse create(
            @Valid @RequestBody ResourceGroupRequest request) {
        long id = resourceGroupService.create(new CreateResourceGroupCommand(
                request.parentId(),
                request.groupCode(),
                request.groupName(),
                request.orderNum(),
                request.status(),
                request.description(),
                operatorUserId()));
        return new ResourceGroupCreatedResponse(id);
    }

    @PreAuthorize("@authz.require('system:resource-group:edit')")
    @PutMapping("/{groupId}")
    public ResponseEntity<Void> update(
            @PathVariable long groupId,
            @Valid @RequestBody ResourceGroupRequest request) {
        resourceGroupService.update(new UpdateResourceGroupCommand(
                groupId,
                request.parentId(),
                request.groupCode(),
                request.groupName(),
                request.orderNum(),
                request.status(),
                request.description(),
                operatorUserId()));
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:resource-group:remove')")
    @DeleteMapping("/{groupId}")
    public ResponseEntity<Void> delete(
            @PathVariable long groupId,
            @RequestParam(required = false) Long migrationTargetGroupId) {
        resourceGroupService.delete(groupId, migrationTargetGroupId, operatorUserId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:resource-group:assign')")
    @PutMapping("/{groupId}/items")
    public ResponseEntity<Void> replaceItems(
            @PathVariable long groupId,
            @Valid @RequestBody ResourceGroupItemsRequest request) {
        resourceGroupService.replaceItems(groupId, request.items(), operatorUserId());
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize("@authz.require('system:resource-group:assign')")
    @PutMapping("/role/{roleId}")
    public ResponseEntity<Void> replaceRoleGroups(
            @PathVariable long roleId,
            @Valid @RequestBody RoleResourceGroupRequest request) {
        resourceGroupService.replaceRoleGroups(
                roleId,
                request.videoScopeType(),
                request.resourceGroupIds(),
                operatorUserId());
        return ResponseEntity.noContent().build();
    }

    private long operatorUserId() {
        AuthPrincipal principal = currentPrincipalService.requireCurrent();
        if (principal.userId() == null || principal.userId() <= 0) {
            throw new AccessDeniedException("资源组管理操作人必须绑定有效用户");
        }
        return principal.userId();
    }
}
