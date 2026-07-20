import request from '@/utils/request'

export function getDepartmentTree() {
  return request({
    method: 'get',
    url: '/api/system/dept/tree'
  })
}

export function createDepartment(data) {
  return request({
    method: 'post',
    url: '/api/system/dept',
    data
  })
}

export function updateDepartment(id, data) {
  return request({
    method: 'put',
    url: `/api/system/dept/${id}`,
    data
  })
}

export function deleteDepartment(id) {
  return request({
    method: 'delete',
    url: `/api/system/dept/${id}`
  })
}
