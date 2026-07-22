import fs from 'fs'
import path from 'path'
import UserForm from '@/views/system/user/components/UserForm.vue'
import RoleForm from '@/views/system/role/components/RoleForm.vue'
import RolePage from '@/views/system/role/index.vue'
import UserPage from '@/views/system/user/index.vue'
import DepartmentPage from '@/views/system/dept/index.vue'
import MenuPage from '@/views/system/menu/index.vue'

describe('system management forms', () => {
  it('系统角色和跨用户 API Key 使用稳定授权标志', () => {
    expect(RolePage.computed.canManageSystemRole.call({
      $store: { getters: { systemUser: true, roles: [] } }
    })).toBe(true)
    expect(RolePage.computed.canManageSystemRole.call({
      $store: { getters: { systemUser: false, roles: ['super_admin'] } }
    })).toBe(false)
    expect(MenuPage.computed.canManageBuiltIn.call({
      $store: { getters: { systemUser: false, roles: ['super_admin'] } }
    })).toBe(false)

    const context = {
      currentUserId: 10,
      $store: { getters: { permissions: ['system:apikey:list'] } },
      isOwnUser: UserPage.methods.isOwnUser
    }
    expect(UserPage.methods.canManageApiKeys.call(context, { id: 10 })).toBe(true)
    expect(UserPage.methods.canManageApiKeys.call(context, { id: 20 })).toBe(false)
    context.$store.getters.permissions.push('system:apikey:manageOthers')
    expect(UserPage.methods.canManageApiKeys.call(context, { id: 20 })).toBe(true)
  })

  it('新增和编辑用户只提交后端允许的字段', () => {
    const form = {
      id: null,
      username: 'operator',
      password: 'safe-password',
      nickname: '值班员',
      deptId: 3,
      roleIds: [7],
      status: 0,
      locked: false
    }
    expect(UserForm.methods.buildPayload.call({ isEdit: false, form })).toEqual({
      username: 'operator',
      password: 'safe-password',
      nickname: '值班员',
      deptId: 3,
      roleIds: [7]
    })

    form.id = 12
    form.status = 1
    form.locked = true
    expect(UserForm.methods.buildPayload.call({ isEdit: true, form })).toEqual({
      nickname: '值班员',
      deptId: 3
    })
  })

  it('角色表单将菜单权限与自定义部门授权分别提交', () => {
    const context = {
      form: {
        id: 7,
        name: '区域管理员',
        roleKey: 'region_admin',
        roleSort: 10,
        deptDataScope: 'CUSTOM',
        videoScopeType: 'NONE',
        status: 0,
        systemRole: false,
        menuIds: [],
        deptIds: []
      },
      isEdit: true,
      getMenuTreeSubmitKeys: RoleForm.methods.getMenuTreeSubmitKeys,
      $refs: {
        menuTree: { getCheckedKeys: () => [1, 2, 2] },
        departmentTree: { getCheckedKeys: () => [3, 4, 4] }
      }
    }

    const payload = RoleForm.methods.buildPayload.call(context)

    expect(payload.menuIds).toEqual([1, 2])
    expect(payload.deptIds).toEqual([3, 4])
    expect(payload).not.toHaveProperty('status')
    expect(payload).not.toHaveProperty('systemRole')
    context.form.deptDataScope = 'SELF'
    expect(RoleForm.methods.buildPayload.call(context).deptIds).toEqual([])
  })

  it('角色菜单树使用父子联动并保存半选父节点', () => {
    const source = fs.readFileSync(
      path.join(process.cwd(), 'src/views/system/role/components/RoleForm.vue'),
      'utf8'
    )
    const menuTreeBlock = source.match(/ref="menuTree"[\s\S]*?<\/el-tree>/)[0]
    const departmentTreeBlock = source.match(/ref="departmentTree"[\s\S]*?<\/el-tree>/)[0]

    expect(menuTreeBlock).not.toContain('check-strictly')
    expect(departmentTreeBlock).toContain('check-strictly')

    const menuOptions = [
      {
        id: 300,
        menuName: '视频管理',
        children: [
          { id: 450, menuName: '控制台', children: [] },
          { id: 460, menuName: '分屏监控', children: [] }
        ]
      }
    ]
    expect(RoleForm.methods.getMenuTreeEchoKeys.call({ menuOptions }, [300, 450]))
      .toEqual([450])

    const context = {
      form: {
        id: 7,
        name: '控制台角色',
        roleKey: 'dashboard_user',
        roleSort: 10,
        deptDataScope: 'SELF',
        videoScopeType: 'NONE',
        menuIds: [],
        deptIds: [],
        resourceGroupIds: []
      },
      isEdit: true,
      getMenuTreeSubmitKeys: RoleForm.methods.getMenuTreeSubmitKeys,
      $refs: {
        menuTree: {
          getCheckedKeys: () => [450, 450],
          getHalfCheckedKeys: () => [300]
        }
      }
    }

    expect(RoleForm.methods.buildPayload.call(context).menuIds).toEqual([450, 300])
  })

  it('部门页面识别 ROOT 与只读路径节点', () => {
    expect(DepartmentPage.methods.isRoot({ parentId: 0 })).toBe(true)
    expect(DepartmentPage.methods.isRoot({ parentId: 1 })).toBe(false)
    expect(DepartmentPage.methods.canOperate({ readOnlyPath: true })).toBe(false)
    expect(DepartmentPage.methods.canOperate({ readOnlyPath: false })).toBe(true)
  })

  it('菜单载荷保留系统内置菜单可调整的显示状态与排序', () => {
    const form = {
      parentId: 100,
      menuName: '用户管理',
      menuType: 'C',
      routeName: 'SystemUser',
      path: 'user',
      component: 'system/user/index',
      perms: '',
      icon: 'user',
      orderNum: 2,
      visible: 1,
      status: 0,
      cacheable: false,
      external: false,
      systemMenu: true
    }

    expect(MenuPage.methods.buildPayload.call({ form })).toEqual({
      parentId: 100,
      menuName: '用户管理',
      menuType: 'C',
      routeName: 'SystemUser',
      path: 'user',
      component: 'system/user/index',
      perms: null,
      icon: 'user',
      orderNum: 2,
      visible: 1,
      status: 0,
      cacheable: false,
      external: false,
      systemMenu: true
    })
  })
})
