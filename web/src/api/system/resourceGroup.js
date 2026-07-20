import request from '@/utils/request'

export function getResourceGroupTree() {
  return request({
    method: 'get',
    url: '/api/system/resource-group/tree'
  })
}

export function getResourceGroupItems(groupId) {
  return request({
    method: 'get',
    url: `/api/system/resource-group/${groupId}/items`
  })
}

export function createResourceGroup(data) {
  return request({
    method: 'post',
    url: '/api/system/resource-group',
    data
  })
}

export function updateResourceGroup(groupId, data) {
  return request({
    method: 'put',
    url: `/api/system/resource-group/${groupId}`,
    data
  })
}

export function deleteResourceGroup(groupId, migrationTargetGroupId) {
  const params = {}
  if (migrationTargetGroupId) params.migrationTargetGroupId = migrationTargetGroupId
  return request({
    method: 'delete',
    url: `/api/system/resource-group/${groupId}`,
    params
  })
}

export function replaceResourceGroupItems(groupId, items) {
  return request({
    method: 'put',
    url: `/api/system/resource-group/${groupId}/items`,
    data: { items }
  })
}

export function getRoleResourceGroupIds(roleId) {
  return request({
    method: 'get',
    url: `/api/system/resource-group/role/${roleId}`
  })
}

export function replaceRoleResourceGroups(roleId, data) {
  return request({
    method: 'put',
    url: `/api/system/resource-group/role/${roleId}`,
    data
  })
}
