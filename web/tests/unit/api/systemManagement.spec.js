jest.mock('@/utils/request', () => jest.fn())

import request from '@/utils/request'
import {
  assignSystemUserRoles,
  changeSystemUserStatus,
  createSystemUser,
  deleteSystemUser,
  getSystemUser,
  getSystemUserReferences,
  querySystemUsers,
  resetSystemUserPassword,
  updateSystemUser
} from '@/api/user'
import {
  changeSystemRoleStatus,
  createSystemRole,
  deleteSystemRole,
  getSystemRole,
  getSystemRoleReferences,
  querySystemRoles,
  updateSystemRole
} from '@/api/role'
import {
  createDepartment,
  deleteDepartment,
  getDepartmentTree,
  updateDepartment
} from '@/api/system/dept'
import {
  createSystemMenu,
  deleteSystemMenu,
  getSystemMenu,
  querySystemMenus,
  updateSystemMenu
} from '@/api/menu'

describe('system management api', () => {
  beforeEach(() => {
    request.mockClear()
  })

  it('用户管理统一使用 /api/system/user JSON 接口', () => {
    const query = { page: 2, count: 20, keyword: 'ops', deptId: 3, status: 0 }
    const createBody = {
      username: 'operator',
      password: 'safe-password',
      nickname: '值班员',
      deptId: 3,
      roleIds: [7]
    }
    const updateBody = {
      nickname: '高级值班员',
      deptId: 4
    }

    querySystemUsers(query)
    getSystemUser(12)
    createSystemUser(createBody)
    updateSystemUser(12, updateBody)
    assignSystemUserRoles(12, [7, 8])
    changeSystemUserStatus(12, { status: 1, locked: false })
    resetSystemUserPassword(12, 'new-safe-password')
    deleteSystemUser(12)
    getSystemUserReferences()

    expect(request).toHaveBeenNthCalledWith(1, {
      method: 'get',
      url: '/api/system/user',
      params: query
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      method: 'get',
      url: '/api/system/user/12'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      method: 'post',
      url: '/api/system/user',
      data: createBody
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      method: 'put',
      url: '/api/system/user/12',
      data: updateBody
    })
    expect(request).toHaveBeenNthCalledWith(5, {
      method: 'put',
      url: '/api/system/user/12/roles',
      data: { roleIds: [7, 8] }
    })
    expect(request).toHaveBeenNthCalledWith(6, {
      method: 'put',
      url: '/api/system/user/12/status',
      data: { status: 1, locked: false }
    })
    expect(request).toHaveBeenNthCalledWith(7, {
      method: 'put',
      url: '/api/system/user/12/password',
      data: { password: 'new-safe-password' }
    })
    expect(request).toHaveBeenNthCalledWith(8, {
      method: 'delete',
      url: '/api/system/user/12'
    })
    expect(request).toHaveBeenNthCalledWith(9, {
      method: 'get',
      url: '/api/system/user/references'
    })
  })

  it('角色管理统一使用 /api/system/role 接口', () => {
    const role = { name: '值班员', roleKey: 'operator', menuIds: [1], deptIds: [] }

    querySystemRoles()
    getSystemRole(7)
    createSystemRole(role)
    updateSystemRole(7, role)
    changeSystemRoleStatus(7, 1)
    deleteSystemRole(7)
    getSystemRoleReferences()

    expect(request).toHaveBeenNthCalledWith(1, {
      method: 'get',
      url: '/api/system/role'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      method: 'get',
      url: '/api/system/role/7'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      method: 'post',
      url: '/api/system/role',
      data: role
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      method: 'put',
      url: '/api/system/role/7',
      data: role
    })
    expect(request).toHaveBeenNthCalledWith(5, {
      method: 'put',
      url: '/api/system/role/7/status',
      data: { status: 1 }
    })
    expect(request).toHaveBeenNthCalledWith(6, {
      method: 'delete',
      url: '/api/system/role/7'
    })
    expect(request).toHaveBeenNthCalledWith(7, {
      method: 'get',
      url: '/api/system/role/references'
    })
  })

  it('部门管理使用树接口和 REST 命令接口', () => {
    const department = {
      parentId: 1,
      deptName: '研发部',
      deptCode: 'RND',
      orderNum: 10,
      leaderUserId: null,
      status: 0
    }

    getDepartmentTree()
    createDepartment(department)
    updateDepartment(3, department)
    deleteDepartment(3)

    expect(request).toHaveBeenNthCalledWith(1, {
      method: 'get',
      url: '/api/system/dept/tree'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      method: 'post',
      url: '/api/system/dept',
      data: department
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      method: 'put',
      url: '/api/system/dept/3',
      data: department
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      method: 'delete',
      url: '/api/system/dept/3'
    })
  })

  it('菜单管理和动态路由接口保持分离', () => {
    const menu = {
      parentId: 0,
      menuName: '自定义菜单',
      menuType: 'M',
      visible: 0,
      status: 0
    }

    querySystemMenus()
    getSystemMenu(8)
    createSystemMenu(menu)
    updateSystemMenu(8, menu)
    deleteSystemMenu(8)

    expect(request).toHaveBeenNthCalledWith(1, {
      method: 'get',
      url: '/api/system/menu'
    })
    expect(request).toHaveBeenNthCalledWith(2, {
      method: 'get',
      url: '/api/system/menu/8'
    })
    expect(request).toHaveBeenNthCalledWith(3, {
      method: 'post',
      url: '/api/system/menu',
      data: menu
    })
    expect(request).toHaveBeenNthCalledWith(4, {
      method: 'put',
      url: '/api/system/menu/8',
      data: menu
    })
    expect(request).toHaveBeenNthCalledWith(5, {
      method: 'delete',
      url: '/api/system/menu/8'
    })
  })
})
