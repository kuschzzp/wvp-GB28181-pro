import request from '@/utils/request'

export function getAll() {
  return request({
    method: 'get',
    url: '/api/role/all'
  })
}

export function querySystemRoles() {
  return request({
    method: 'get',
    url: '/api/system/role'
  })
}

export function getSystemRoleReferences() {
  return request({
    method: 'get',
    url: '/api/system/role/references'
  })
}

export function getSystemRole(id) {
  return request({
    method: 'get',
    url: `/api/system/role/${id}`
  })
}

export function createSystemRole(data) {
  return request({
    method: 'post',
    url: '/api/system/role',
    data
  })
}

export function updateSystemRole(id, data) {
  return request({
    method: 'put',
    url: `/api/system/role/${id}`,
    data
  })
}

export function changeSystemRoleStatus(id, status) {
  return request({
    method: 'put',
    url: `/api/system/role/${id}/status`,
    data: { status }
  })
}

export function deleteSystemRole(id) {
  return request({
    method: 'delete',
    url: `/api/system/role/${id}`
  })
}
