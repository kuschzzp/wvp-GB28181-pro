jest.mock('@/utils/request', () => jest.fn())

import request from '@/utils/request'
import { add, getScopeOptions, updateScope } from '@/api/userApiKey'

describe('userApiKey api', () => {
  beforeEach(() => {
    request.mockClear()
  })

  it('创建 API Key 使用主 JSON 接口且不回退到查询参数', () => {
    add({
      userId: 12,
      app: 'integration',
      enable: true,
      expiresAt: '2026-07-14 10:00:00',
      remark: 'automation',
      permissionMenuIds: [31, 32],
      resourceGroupIds: [9],
      ipAllowlist: ['10.0.0.0/8']
    })

    expect(request).toHaveBeenCalledWith({
      method: 'post',
      url: '/api/userApiKey',
      data: {
        ownerId: 12,
        app: 'integration',
        enabled: true,
        expiresAt: '2026-07-14 10:00:00',
        remark: 'automation',
        permissionMenuIds: [31, 32],
        resourceGroupIds: [9],
        ipAllowlist: ['10.0.0.0/8']
      }
    })
  })

  it('使用独立 PUT 接口更新范围并携带目标用户', () => {
    updateScope({
      id: 7,
      userId: 12,
      permissionMenuIds: [31],
      resourceGroupIds: [9, 10],
      ipAllowlist: ['192.168.1.10']
    })

    expect(request).toHaveBeenCalledWith({
      method: 'put',
      url: '/api/userApiKey/7/scope',
      params: { userId: 12 },
      data: {
        permissionMenuIds: [31],
        resourceGroupIds: [9, 10],
        ipAllowlist: ['192.168.1.10']
      }
    })
  })

  it('通过 API Key 管理权限加载目标用户的可选范围', () => {
    getScopeOptions(12)

    expect(request).toHaveBeenCalledWith({
      method: 'get',
      url: '/api/userApiKey/scope-options',
      params: { userId: 12 }
    })
  })
})
