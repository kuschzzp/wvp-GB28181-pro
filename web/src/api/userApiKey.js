import request from '@/utils/request'

export function remark(params) {
  const { id, remark } = params
  return request({
    method: 'post',
    url: '/api/userApiKey/remark',
    params: {
      id: id,
      remark: remark
    }
  })
}

export function queryList(params) {
  const { page, count, userId } = params
  return request({
    method: 'get',
    url: '/api/userApiKey/userApiKeys',
    params: {
      page: page,
      count: count,
      userId: userId
    }
  })
}

export function getScopeOptions(userId) {
  const params = {}
  if (userId !== null && userId !== undefined) params.userId = userId
  return request({
    method: 'get',
    url: '/api/userApiKey/scope-options',
    params
  })
}

export function enable(id) {
  return request({
    method: 'post',
    url: `/api/userApiKey/enable?id=${id}`

  })
}

export function disable(id) {
  return request({
    method: 'post',
    url: `/api/userApiKey/disable?id=${id}`
  })
}

export function reset(id) {
  return request({
    method: 'post',
    url: `/api/userApiKey/reset?id=${id}`
  })
}

export function remove(id) {
  return request({
    method: 'delete',
    url: `/api/userApiKey/delete?id=${id}`
  })
}

export function add(params) {
  const {
    userId,
    app,
    enable,
    expiresAt,
    remark,
    permissionMenuIds = [],
    resourceGroupIds = [],
    ipAllowlist = []
  } = params
  return request({
    method: 'post',
    url: '/api/userApiKey',
    data: {
      ownerId: userId,
      app: app,
      enabled: enable,
      expiresAt: expiresAt,
      remark: remark,
      permissionMenuIds,
      resourceGroupIds,
      ipAllowlist
    }
  })
}

export function updateScope(params) {
  const {
    id,
    userId,
    permissionMenuIds = [],
    resourceGroupIds = [],
    ipAllowlist = []
  } = params
  const requestParams = {}
  if (userId !== null && userId !== undefined) requestParams.userId = userId
  return request({
    method: 'put',
    url: `/api/userApiKey/${id}/scope`,
    params: requestParams,
    data: {
      permissionMenuIds,
      resourceGroupIds,
      ipAllowlist
    }
  })
}
