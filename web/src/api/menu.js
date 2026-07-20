import request from '@/utils/request'

export function getRouters() {
  return request({
    url: '/api/menu/routers',
    method: 'get'
  })
}

export function querySystemMenus() {
  return request({
    method: 'get',
    url: '/api/system/menu'
  })
}

export function getSystemMenu(id) {
  return request({
    method: 'get',
    url: `/api/system/menu/${id}`
  })
}

export function createSystemMenu(data) {
  return request({
    method: 'post',
    url: '/api/system/menu',
    data
  })
}

export function updateSystemMenu(id, data) {
  return request({
    method: 'put',
    url: `/api/system/menu/${id}`,
    data
  })
}

export function deleteSystemMenu(id) {
  return request({
    method: 'delete',
    url: `/api/system/menu/${id}`
  })
}
