jest.mock('@/utils/request', () => jest.fn())

import {
  collectInheritedResourceGroupIds,
  mergeResourceItems,
  normalizeResourceItems,
  serializeResourceItems
} from '@/utils/videoResourceAuthorization'
import { componentMap } from '@/store/modules/permission'
import ResourceGroupPage from '@/views/system/resourceGroup/index.vue'
import ResourceGroupTree from '@/views/system/resourceGroup/components/ResourceGroupTree.vue'
import ResourceItemDialog from '@/views/system/resourceGroup/components/ResourceItemDialog.vue'
import RoleForm from '@/views/system/role/components/RoleForm.vue'

describe('video resource group management', () => {
  const groups = [
    {
      id: 1,
      parentId: 0,
      groupCode: 'ROOT_VIDEO',
      groupName: '全部视频资源组',
      status: 0,
      children: [
        {
          id: 2,
          parentId: 1,
          groupCode: 'EAST',
          groupName: '东区',
          status: 0,
          children: [
            {
              id: 3,
              parentId: 2,
              groupCode: 'EAST_GATE',
              groupName: '东门',
              status: 0,
              children: []
            }
          ]
        },
        {
          id: 4,
          parentId: 1,
          groupCode: 'DISABLED',
          groupName: '停用组',
          status: 1,
          children: []
        }
      ]
    }
  ]

  it('规范化并去重六类资源，提交时只发送后端 VideoResourceRef 字段', () => {
    const normalized = normalizeResourceItems([
      { resourceType: 'GB_DEVICE', resourceId: 11 },
      { type: 'GB_DEVICE', id: 11, label: '门口设备' },
      { type: 'CHANNEL', id: 21, label: '东门通道' },
      { type: 'JT_TERMINAL', id: 31 },
      { type: 'JT_CHANNEL', id: 41 },
      { type: 'STREAM_PROXY', id: 51 },
      { type: 'STREAM_PUSH', id: 61 },
      { type: 'CHANNEL', id: 0 },
      null
    ])

    expect(normalized).toHaveLength(6)
    expect(normalized[0]).toMatchObject({ resourceType: 'GB_DEVICE', resourceId: 11 })
    expect(serializeResourceItems(normalized)).toEqual([
      { type: 'GB_DEVICE', id: 11 },
      { type: 'CHANNEL', id: 21 },
      { type: 'JT_TERMINAL', id: 31 },
      { type: 'JT_CHANNEL', id: 41 },
      { type: 'STREAM_PROXY', id: 51 },
      { type: 'STREAM_PUSH', id: 61 }
    ])
    expect(mergeResourceItems(normalized, [
      { type: 'CHANNEL', id: 21 },
      { type: 'CHANNEL', id: 22 }
    ])).toHaveLength(7)
  })

  it('父组授权会标记全部后代，显式选择与继承展示保持分离', () => {
    expect([...collectInheritedResourceGroupIds(groups, [2])]).toEqual([3])
    expect([...collectInheritedResourceGroupIds(groups, [1, 3])]).toEqual([2, 4])
    expect(ResourceGroupTree.methods.filterNode('east_gate', groups[0].children[0].children[0]))
      .toBe(true)
    expect(ResourceGroupTree.methods.nodeDisabled.call({ checkable: true, readonly: false }, groups[0].children[1]))
      .toBe(true)
  })

  it('整台设备提示、单通道选择和页面合并都使用稳定内部资源 ID', () => {
    expect(ResourceItemDialog.methods.typeHint.call({ activeType: 'GB_DEVICE' }))
      .toContain('以后同步的新通道')
    expect(ResourceItemDialog.methods.typeHint.call({ activeType: 'CHANNEL' }))
      .toContain('仅授权当前通道')

    const merged = ResourceGroupPage.methods.mergeItems.call({},
      [{ resourceType: 'CHANNEL', resourceId: 8 }],
      [{ type: 'CHANNEL', id: 8 }, { type: 'CHANNEL', id: 9 }])
    expect(merged.map(item => item.resourceId)).toEqual([8, 9])
  })

  it('角色 CUSTOM 范围提交显式资源组，ALL/NONE 不携带残留 ID', () => {
    const context = {
      form: {
        id: 7,
        name: '区域管理员',
        roleKey: 'region_admin',
        roleSort: 10,
        deptDataScope: 'SELF',
        videoScopeType: 'CUSTOM',
        status: 0,
        systemRole: false,
        menuIds: [],
        deptIds: [],
        resourceGroupIds: [2, 2, 3]
      },
      isEdit: true,
      $refs: {
        menuTree: { getCheckedKeys: () => [1] },
        resourceGroupTree: { getCheckedKeys: () => [2, 2, 3] }
      }
    }

    expect(RoleForm.methods.buildPayload.call(context).resourceGroupIds).toEqual([2, 3])
    context.form.videoScopeType = 'ALL'
    expect(RoleForm.methods.buildPayload.call(context).resourceGroupIds).toEqual([])
  })

  it('无 assign 权限时视频范围只读，资源组页面已加入固定组件白名单', () => {
    expect(RoleForm.computed.canAssignVideoResources.call({
      $auth: { hasPermi: () => false }
    })).toBe(false)
    expect(RoleForm.computed.canAssignVideoResources.call({
      $auth: { hasPermi: permissions => permissions.includes('system:resource-group:assign') }
    })).toBe(true)
    expect(componentMap['system/resource-group/index']).toEqual(expect.any(Function))
  })

  it('无 assign 权限保存角色时原样保留既有视频范围', () => {
    const context = {
      form: {
        id: 7,
        name: '区域管理员',
        roleKey: 'region_admin',
        roleSort: 10,
        deptDataScope: 'SELF',
        videoScopeType: 'ALL',
        menuIds: [],
        deptIds: [],
        resourceGroupIds: []
      },
      isEdit: true,
      canAssignVideoResources: false,
      originalVideoScopeType: 'CUSTOM',
      originalResourceGroupIds: [2, 3],
      $refs: {
        menuTree: { getCheckedKeys: () => [] }
      }
    }

    expect(RoleForm.methods.buildPayload.call(context)).toMatchObject({
      videoScopeType: 'CUSTOM',
      resourceGroupIds: [2, 3]
    })
  })
})
