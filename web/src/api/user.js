import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/api/auth/login',
    method: 'post',
    data
  })
}

export function logout() {
  return request({
    url: '/api/auth/logout',
    method: 'post'
  })
}

export function getProfile() {
  return request({
    url: '/api/auth/profile',
    method: 'get'
  })
}

export function changePushKey(params) {
  const { pushKey, userId } = params
  return request({
    method: 'post',
    url: '/api/user/changePushKey',
    params: {
      pushKey: pushKey,
      userId: userId
    }
  })
}

export function queryList(params) {
  const { page, count } = params
  return request({
    method: 'get',
    url: '/api/user/users',
    params: {
      page: page,
      count: count
    }
  })
}

export function removeById(id) {
  return request({
    method: 'delete',
    url: `/api/user/delete?id=${id}`

  })
}

export function add(params) {
  const { username, password, roleId } = params
  return request({
    method: 'post',
    url: '/api/user',
    data: {
      username: username,
      password: password,
      nickname: username,
      deptId: null,
      roleIds: [roleId]
    }
  })
}

export function changePassword(params) {
  const { oldPassword, password } = params
  return request({
    method: 'post',
    url: '/api/user/changePassword',
    params: {
      oldPassword: oldPassword,
      password: password
    }
  })
}

export function changePasswordForAdmin(params) {
  const { password, userId } = params
  return request({
    method: 'post',
    url: '/api/user/changePasswordForAdmin',
    params: {
      password: password,
      userId: userId
    }
  })
}

export function querySystemUsers(params = {}) {
  return request({
    method: 'get',
    url: '/api/system/user',
    params
  })
}

export function getSystemUserReferences() {
  return request({
    method: 'get',
    url: '/api/system/user/references'
  })
}

export function getSystemUser(id) {
  return request({
    method: 'get',
    url: `/api/system/user/${id}`
  })
}

export function createSystemUser(data) {
  return request({
    method: 'post',
    url: '/api/system/user',
    data
  })
}

export function updateSystemUser(id, data) {
  return request({
    method: 'put',
    url: `/api/system/user/${id}`,
    data
  })
}

export function assignSystemUserRoles(id, roleIds) {
  return request({
    method: 'put',
    url: `/api/system/user/${id}/roles`,
    data: { roleIds }
  })
}

export function changeSystemUserStatus(id, data) {
  return request({
    method: 'put',
    url: `/api/system/user/${id}/status`,
    data
  })
}

export function resetSystemUserPassword(id, password) {
  return request({
    method: 'put',
    url: `/api/system/user/${id}/password`,
    data: { password }
  })
}

export function deleteSystemUser(id) {
  return request({
    method: 'delete',
    url: `/api/system/user/${id}`
  })
}
